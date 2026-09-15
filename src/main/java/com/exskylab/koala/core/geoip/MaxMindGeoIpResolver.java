package com.exskylab.koala.core.geoip;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public final class MaxMindGeoIpResolver implements GeoIpResolver, AutoCloseable {

    private final DatabaseReader reader;

    private MaxMindGeoIpResolver(DatabaseReader reader) {
        this.reader = reader;
    }

    public static MaxMindGeoIpResolver open(Path database) throws IOException {
        DatabaseReader reader = new DatabaseReader.Builder(database.toFile()).withCache(new CHMCache()).build();
        return new MaxMindGeoIpResolver(reader);
    }

    @Override
    public Optional<String> locate(String ipAddress) {
        InetAddress address = publicAddress(ipAddress);
        if (address == null) {
            return Optional.empty();
        }
        try {
            return reader.tryCity(address).map(MaxMindGeoIpResolver::describe).filter(value -> !value.isBlank());
        } catch (Exception lookupFailed) {
            log.debug("GeoIP lookup failed: {}", lookupFailed.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
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
