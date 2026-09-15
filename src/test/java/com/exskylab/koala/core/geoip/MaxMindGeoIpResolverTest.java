package com.exskylab.koala.core.geoip;

import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.DatabaseReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaxMindGeoIpResolverTest {

    @Test
    void privateLoopbackAndMalformedAddressesAreNeverLookedUp() {
        assertNull(MaxMindGeoIpResolver.publicAddress("127.0.0.1"));
        assertNull(MaxMindGeoIpResolver.publicAddress("10.1.2.3"));
        assertNull(MaxMindGeoIpResolver.publicAddress("192.168.1.10"));
        assertNull(MaxMindGeoIpResolver.publicAddress("::1"));
        assertNull(MaxMindGeoIpResolver.publicAddress("unknown"));
        assertNull(MaxMindGeoIpResolver.publicAddress("evil.example.com"));
        assertNull(MaxMindGeoIpResolver.publicAddress(null));
        assertNull(MaxMindGeoIpResolver.publicAddress(" "));
    }

    @Test
    void aPublicAddressIsAccepted() {
        assertNotNull(MaxMindGeoIpResolver.publicAddress("203.0.113.7"));
        assertNotNull(MaxMindGeoIpResolver.publicAddress("2001:db8::1"));
    }

    @Test
    void theLocationIsCityAndCountryCodeFallingBackToWhatIsKnown() {
        City istanbul = new City(List.of("en"), null, 745044L, Map.of("en", "Istanbul"));
        Country turkiye = new Country(List.of("en"), null, 298795L, false, "TR", Map.of("en", "Turkey"));

        assertEquals("Istanbul, TR", MaxMindGeoIpResolver.describe(
                new CityResponse(istanbul, null, turkiye, null, null, null, null, null, null, null)));
        assertEquals("TR", MaxMindGeoIpResolver.describe(
                new CityResponse(null, null, turkiye, null, null, null, null, null, null, null)));
        assertEquals("", MaxMindGeoIpResolver.describe(
                new CityResponse(null, null, null, null, null, null, null, null, null, null)));
    }

    private static DatabaseReader emptyReader() {
        DatabaseReader reader = mock(DatabaseReader.class);
        try {
            when(reader.tryCity(any(InetAddress.class))).thenReturn(Optional.empty());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
        return reader;
    }

    @Test
    void aReplacedDatabaseFileIsReopenedBeforeTheNextLookupAndTheOldReaderIsClosed(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("GeoLite2-City.mmdb");
        Files.writeString(file, "v1");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-09-01T00:00:00Z")));
        ArrayList<DatabaseReader> opened = new ArrayList<>();
        MaxMindGeoIpResolver resolver = new MaxMindGeoIpResolver(file, database -> {
            DatabaseReader reader = emptyReader();
            opened.add(reader);
            return reader;
        });

        resolver.locate("203.0.113.7");
        assertEquals(1, opened.size(), "degismeyen dosya yeniden acilmamali");

        Files.writeString(file, "v2");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-09-08T00:00:00Z")));
        resolver.locate("203.0.113.7");
        resolver.locate("203.0.113.7");

        assertEquals(2, opened.size(), "mtime degisince tam bir kez yeniden acilmali");
        verify(opened.get(0)).close();
        verify(opened.get(0)).tryCity(any(InetAddress.class));
        verify(opened.get(1), times(2)).tryCity(any(InetAddress.class));
        assertEquals(FileTime.from(Instant.parse("2026-09-08T00:00:00Z")), resolver.loadedAt());
    }

    @Test
    void aHalfWrittenReplacementKeepsThePreviousReaderAndIsNotRetriedUntilItChangesAgain(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("GeoLite2-City.mmdb");
        Files.writeString(file, "v1");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-09-01T00:00:00Z")));
        ArrayList<DatabaseReader> opened = new ArrayList<>();
        int[] attempts = {0};
        MaxMindGeoIpResolver resolver = new MaxMindGeoIpResolver(file, database -> {
            attempts[0]++;
            if (attempts[0] == 2) {
                throw new IOException("truncated");
            }
            DatabaseReader reader = emptyReader();
            opened.add(reader);
            return reader;
        });

        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-09-08T00:00:00Z")));
        resolver.locate("203.0.113.7");
        resolver.locate("203.0.113.7");

        assertEquals(2, attempts[0], "bozuk dosya her aramada yeniden denenmemeli");
        assertEquals(1, opened.size());
        verify(opened.get(0), times(2)).tryCity(any(InetAddress.class));

        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-09-15T00:00:00Z")));
        resolver.locate("203.0.113.7");

        assertEquals(3, attempts[0], "dosya bir daha degisince yeniden denenmeli");
        assertEquals(2, opened.size());
        verify(opened.get(0)).close();
    }
}
