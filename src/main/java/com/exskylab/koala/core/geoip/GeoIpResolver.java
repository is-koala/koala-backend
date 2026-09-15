package com.exskylab.koala.core.geoip;

import java.util.Optional;

public interface GeoIpResolver {

    Optional<String> locate(String ipAddress);

    boolean isAvailable();

    static GeoIpResolver unavailable() {
        return new GeoIpResolver() {
            @Override
            public Optional<String> locate(String ipAddress) {
                return Optional.empty();
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
    }
}
