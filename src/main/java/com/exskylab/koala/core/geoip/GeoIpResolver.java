package com.exskylab.koala.core.geoip;

import java.util.Optional;

public interface GeoIpResolver {

    Optional<String> locate(String ipAddress);

    static GeoIpResolver unavailable() {
        return ipAddress -> Optional.empty();
    }
}
