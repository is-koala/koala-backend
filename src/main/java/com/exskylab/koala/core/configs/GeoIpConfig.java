package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.geoip.GeoIpResolver;
import com.exskylab.koala.core.geoip.MaxMindGeoIpResolver;
import com.exskylab.koala.core.properties.GeoIpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Slf4j
public class GeoIpConfig {

    @Bean
    public GeoIpResolver geoIpResolver(GeoIpProperties properties) {
        if (properties.getDatabasePath() == null) {
            log.warn("GeoIP database path is not configured; session locations will stay empty");
            return GeoIpResolver.unavailable();
        }
        Path database = Path.of(properties.getDatabasePath());
        if (!Files.isRegularFile(database)) {
            log.warn("GeoIP database not found at {}; session locations will stay empty", database);
            return GeoIpResolver.unavailable();
        }
        try {
            MaxMindGeoIpResolver resolver = MaxMindGeoIpResolver.open(database);
            log.info("GeoIP database loaded from {}", database);
            return resolver;
        } catch (IOException unreadable) {
            log.warn("GeoIP database at {} could not be opened ({}); session locations will stay empty",
                    database, unreadable.getClass().getSimpleName());
            return GeoIpResolver.unavailable();
        }
    }
}
