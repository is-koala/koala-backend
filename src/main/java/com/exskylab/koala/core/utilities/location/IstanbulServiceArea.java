package com.exskylab.koala.core.utilities.location;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

public final class IstanbulServiceArea {
    public static final double SOUTH = 40.80;
    public static final double WEST = 28.55;
    public static final double NORTH = 41.35;
    public static final double EAST = 29.50;

    private IstanbulServiceArea() {
    }

    public static boolean contains(BigDecimal latitude, BigDecimal longitude) {
        return latitude.compareTo(BigDecimal.valueOf(SOUTH)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(NORTH)) <= 0
                && longitude.compareTo(BigDecimal.valueOf(WEST)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(EAST)) <= 0;
    }

    public static boolean supportsCountry(String countryCode) {
        return "TR".equalsIgnoreCase(countryCode);
    }

    public static boolean supportsCity(String cityName) {
        return "ISTANBUL".equals(canonicalName(cityName));
    }

    public static String canonicalName(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replace('İ', 'I')
                .replace('ı', 'I');
        return decomposed.replaceAll("\\s+ILCESI$", "").trim();
    }
}
