package com.exskylab.koala.core.utilities.location;

import com.exskylab.koala.core.properties.AddressSearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleGeocodingAddressProviderTest {
    private MockRestServiceServer server;
    private GoogleGeocodingAddressProvider provider;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        AddressSearchProperties properties = new AddressSearchProperties();
        properties.setEnabled(true);
        properties.setApiKey("server-restricted-test-key");
        properties.setGeocodingBaseUrl("https://geocoding.test");
        provider = new GoogleGeocodingAddressProvider(restTemplate, new ObjectMapper(), properties);
    }

    @Test
    void reverseGeocodingMapsComponentsButKeepsTheUserSelectedPin() {
        server.expect(requestTo(containsString("https://geocoding.test/maps/api/geocode/json")))
                .andExpect(requestTo(containsString("latlng=40.982512")))
                .andExpect(requestTo(containsString("29.025411")))
                .andExpect(requestTo(containsString("language=tr")))
                .andExpect(requestTo(containsString("region=tr")))
                .andRespond(withSuccess("""
                        {"status":"OK","results":[{"address_components":[
                          {"long_name":"İstanbul","short_name":"İstanbul","types":["administrative_area_level_1"]},
                          {"long_name":"12","short_name":"12","types":["street_number"]},
                          {"long_name":"Türkiye","short_name":"TR","types":["country"]},
                          {"long_name":"Kadıköy","short_name":"Kadıköy","types":["administrative_area_level_2"]},
                          {"long_name":"Moda Caddesi","short_name":"Moda Cd.","types":["route"]},
                          {"long_name":"Caferağa Mahallesi","short_name":"Caferağa","types":["neighborhood"]},
                          {"long_name":"34710","short_name":"34710","types":["postal_code"]}
                        ]}]}
                        """, MediaType.APPLICATION_JSON));

        ProviderAddressCandidate candidate = provider.reverse(
                new BigDecimal("40.982512"), new BigDecimal("29.025411")).orElseThrow();

        assertEquals("TR", candidate.countryCode());
        assertEquals("Kadıköy", candidate.district());
        assertEquals("Caferağa Mahallesi", candidate.neighborhood());
        assertEquals(new BigDecimal("40.982512"), candidate.latitude());
        server.verify();
    }

    @Test
    void noAddressAtThePinIsAValidEmptyResult() {
        server.expect(requestTo(containsString("https://geocoding.test/maps/api/geocode/json")))
                .andRespond(withSuccess("{\"status\":\"ZERO_RESULTS\",\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        assertTrue(provider.reverse(new BigDecimal("41.100000"), new BigDecimal("29.100000")).isEmpty());
        server.verify();
    }
}
