package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.geoip.GeoIpResolver;
import com.exskylab.koala.core.properties.GeoIpProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoIpConfigTest {

    private final GeoIpConfig config = new GeoIpConfig();

    @Test
    void noPathMeansNoLookupsAndNoFailure() {
        GeoIpProperties properties = new GeoIpProperties();
        properties.setDatabasePath("   ");

        GeoIpResolver resolver = config.geoIpResolver(properties);

        assertTrue(resolver.locate("203.0.113.7").isEmpty());
    }

    @Test
    void aMissingFileIsAWarningNotAStartupFailure() {
        GeoIpProperties properties = new GeoIpProperties();
        properties.setDatabasePath("/nonexistent/GeoLite2-City.mmdb");

        GeoIpResolver resolver = config.geoIpResolver(properties);

        assertTrue(resolver.locate("203.0.113.7").isEmpty());
    }

    @Test
    void anUnreadableFileIsAWarningNotAStartupFailure(@TempDir Path directory) throws Exception {
        Path garbage = directory.resolve("GeoLite2-City.mmdb");
        Files.writeString(garbage, "bu bir mmdb degil");
        GeoIpProperties properties = new GeoIpProperties();
        properties.setDatabasePath(garbage.toString());

        GeoIpResolver resolver = config.geoIpResolver(properties);

        assertTrue(resolver.locate("203.0.113.7").isEmpty());
    }
}
