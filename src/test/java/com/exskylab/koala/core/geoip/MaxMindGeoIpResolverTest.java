package com.exskylab.koala.core.geoip;

import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
