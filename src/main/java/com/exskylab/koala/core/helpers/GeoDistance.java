package com.exskylab.koala.core.helpers;

import java.math.BigDecimal;

public final class GeoDistance {

    private static final double EARTH_METERS = 6_371_000;

    private GeoDistance() {
    }

    public static double metersBetween(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLambda = Math.toRadians(lon2.subtract(lon1).doubleValue());
        double sinHalfPhi = Math.sin(deltaPhi / 2.0);
        double sinHalfLambda = Math.sin(deltaLambda / 2.0);
        double a = sinHalfPhi * sinHalfPhi
                + Math.cos(phi1) * Math.cos(phi2) * sinHalfLambda * sinHalfLambda;
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)));
        return EARTH_METERS * c;
    }
}
