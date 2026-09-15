package com.exskylab.koala.core.geoip;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

@Slf4j
public final class MaxMindGeoIpResolver implements GeoIpResolver, AutoCloseable {

    interface ReaderSource {
        DatabaseReader open(Path database) throws IOException;
    }

    static final ReaderSource MAXMIND = database ->
            new DatabaseReader.Builder(database.toFile()).withCache(new CHMCache()).build();

    private final Path database;
    private final ReaderSource source;
    private final Object swap = new Object();
    private volatile DatabaseReader reader;
    private volatile FileTime loadedAt;
    private volatile FileTime rejectedAt;

    MaxMindGeoIpResolver(Path database, ReaderSource source) throws IOException {
        this.database = database;
        this.source = source;
        this.reader = source.open(database);
        this.loadedAt = modifiedAt();
    }

    public static MaxMindGeoIpResolver open(Path database) throws IOException {
        return new MaxMindGeoIpResolver(database, MAXMIND);
    }

    FileTime loadedAt() {
        return loadedAt;
    }

    private FileTime modifiedAt() {
        try {
            return Files.getLastModifiedTime(database);
        } catch (IOException unreadable) {
            return loadedAt;
        }
    }

    private void reopenIfReplaced() {
        FileTime current = modifiedAt();
        if (current == null || current.equals(loadedAt) || current.equals(rejectedAt)) {
            return;
        }
        synchronized (swap) {
            if (current.equals(loadedAt) || current.equals(rejectedAt)) {
                return;
            }
            try {
                DatabaseReader replacement = source.open(database);
                DatabaseReader previous = reader;
                reader = replacement;
                loadedAt = current;
                rejectedAt = null;
                previous.close();
                log.info("GeoIP database reloaded from {} (modified {})", database, current);
            } catch (IOException unreadable) {
                rejectedAt = current;
                log.warn("GeoIP database at {} changed but could not be reopened ({}); keeping the previous copy",
                        database, unreadable.getClass().getSimpleName());
            }
        }
    }

    @Override
    public Optional<String> locate(String ipAddress) {
        InetAddress address = publicAddress(ipAddress);
        if (address == null) {
            return Optional.empty();
        }
        reopenIfReplaced();
        try {
            return reader.tryCity(address).map(MaxMindGeoIpResolver::describe).filter(value -> !value.isBlank());
        } catch (Exception lookupFailed) {
            log.debug("GeoIP lookup failed: {}", lookupFailed.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    static String describe(CityResponse response) {
        String city = response.getCity() == null ? null : response.getCity().getName();
        String country = response.getCountry() == null ? null : response.getCountry().getIsoCode();
        if (city != null && !city.isBlank() && country != null && !country.isBlank()) {
            return city + ", " + country;
        }
        if (country != null && !country.isBlank()) {
            return country;
        }
        return city == null ? "" : city;
    }

    static InetAddress publicAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        String candidate = ipAddress.trim();
        if (!candidate.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(candidate);
        } catch (UnknownHostException invalid) {
            return null;
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
            return null;
        }
        return address;
    }
}
