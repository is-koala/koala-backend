package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoDistanceTest {

    @Test
    void theSamePointIsZeroMeters() {
        BigDecimal lat = new BigDecimal("41.048000");
        BigDecimal lng = new BigDecimal("29.009000");
        assertEquals(0.0, GeoDistance.metersBetween(lat, lng, lat, lng), 0.001);
    }

    @Test
    void aDistantPinIsFartherThanTheBoardingThreshold() {
        double meters = GeoDistance.metersBetween(
                new BigDecimal("41.0480"), new BigDecimal("29.0090"),
                new BigDecimal("40.0000"), new BigDecimal("29.0000"));
        assertTrue(meters > 250.0, "expected far pin, was " + meters);
    }
}
