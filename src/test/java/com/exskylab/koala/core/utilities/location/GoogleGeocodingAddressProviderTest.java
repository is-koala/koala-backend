package com.exskylab.koala.core.utilities.location;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.core.dtos.address.response.AddressCandidatePrecision;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.properties.AddressSearchProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleGeocodingAddressProviderTest {
    private static final String API_KEY = "server-restricted-test-key";
    private static final String BUILDING_TIER = "result_type=street_address%7Cpremise";
    private static final String AREA_TIER = "result_type=route%7Csublocality%7Cneighborhood%7Cpostal_code";
    private static final String ZERO_RESULTS = "{\"status\":\"ZERO_RESULTS\",\"results\":[]}";
    private static final BigDecimal LATITUDE = new BigDecimal("40.982512");
    private static final BigDecimal LONGITUDE = new BigDecimal("29.025411");

    private MockRestServiceServer server;
    private GoogleGeocodingAddressProvider provider;
    private ListAppender<ILoggingEvent> logLines;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        AddressSearchProperties properties = new AddressSearchProperties();
        properties.setEnabled(true);
        properties.setApiKey(API_KEY);
        properties.setGeocodingBaseUrl("https://geocoding.test");
        provider = new GoogleGeocodingAddressProvider(restTemplate, new ObjectMapper(), properties);
        logLines = new ListAppender<>();
        logLines.start();
        logger().addAppender(logLines);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(logLines);
    }

    @Test
    void reverseGeocodingMapsComponentsButKeepsTheUserSelectedPin() {
        server.expect(requestTo(containsString("https://geocoding.test/maps/api/geocode/json")))
                .andExpect(requestTo(containsString("latlng=40.982512")))
                .andExpect(requestTo(containsString("29.025411")))
                .andExpect(requestTo(containsString("language=tr")))
                .andExpect(requestTo(containsString("region=tr")))
                .andExpect(requestTo(containsString(BUILDING_TIER)))
                .andExpect(requestTo(not(containsString("%25"))))
                .andRespond(withSuccess(results(streetAddress()), MediaType.APPLICATION_JSON));

        ProviderAddressCandidate candidate = provider.reverse(LATITUDE, LONGITUDE).orElseThrow();

        assertEquals("TR", candidate.countryCode());
        assertEquals("Kadıköy", candidate.district());
        assertEquals("Caferağa Mahallesi", candidate.neighborhood());
        assertEquals("12", candidate.buildingNumber());
        assertEquals(AddressCandidatePrecision.BUILDING, candidate.precision());
        assertEquals(LATITUDE, candidate.latitude());
        server.verify();
    }

    @Test
    void theMostDetailedOfSeveralBuildingTierResultsWins() {
        server.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(results(routeOnly(), neighborhoodOnly(), streetAddress()),
                        MediaType.APPLICATION_JSON));

        ProviderAddressCandidate candidate = provider.reverse(LATITUDE, LONGITUDE).orElseThrow();

        assertEquals("12", candidate.buildingNumber());
        assertEquals("Moda Caddesi", candidate.thoroughfare());
        assertEquals(AddressCandidatePrecision.BUILDING, candidate.precision());
        server.verify();
    }

    @Test
    void aStreetLevelResultIsFetchedWhenNoBuildingIsKnownAtThePin() {
        server.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(AREA_TIER)))
                .andExpect(requestTo(containsString("latlng=40.982512")))
                .andRespond(withSuccess(results(routeOnly()), MediaType.APPLICATION_JSON));

        ProviderAddressCandidate candidate = provider.reverse(LATITUDE, LONGITUDE).orElseThrow();

        assertEquals(AddressCandidatePrecision.STREET, candidate.precision());
        assertEquals("Moda Caddesi", candidate.thoroughfare());
        assertEquals("Kadıköy", candidate.district());
        assertEquals("34710", candidate.postalCode());
        assertNull(candidate.buildingNumber());
        server.verify();
    }

    @Test
    void aNeighborhoodLevelResultIsTheLastResortBeforeGivingUp() {
        server.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(AREA_TIER)))
                .andRespond(withSuccess(results(neighborhoodOnly()), MediaType.APPLICATION_JSON));

        ProviderAddressCandidate candidate = provider.reverse(LATITUDE, LONGITUDE).orElseThrow();

        assertEquals(AddressCandidatePrecision.NEIGHBORHOOD, candidate.precision());
        assertEquals("Caferağa Mahallesi", candidate.neighborhood());
        assertNull(candidate.thoroughfare());
        assertNull(candidate.buildingNumber());
        server.verify();
    }

    @Test
    void noAddressAtThePinIsAValidEmptyResultAfterBothTiers() {
        server.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(AREA_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));

        assertTrue(provider.reverse(new BigDecimal("41.100000"), new BigDecimal("29.100000")).isEmpty());
        assertTrue(logLines.list.isEmpty());
        server.verify();
    }

    @Test
    void aDeniedRequestNamesTheProviderStatusWithoutThePreciseCoordinateOrTheKey() {
        server.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess("{\"status\":\"REQUEST_DENIED\",\"results\":[],"
                        + "\"error_message\":\"This API key is not authorized\"}", MediaType.APPLICATION_JSON));

        assertThrows(ExternalServiceException.class, () -> provider.reverse(LATITUDE, LONGITUDE));

        assertEquals(1, logLines.list.size());
        ILoggingEvent line = logLines.list.getFirst();
        assertEquals("WARN", line.getLevel().toString());
        String message = line.getFormattedMessage();
        assertTrue(message.contains("REQUEST_DENIED"), message);
        assertTrue(message.contains("40.98,29.03"), message);
        assertFalse(message.contains("40.982512"), message);
        assertFalse(message.contains("29.025411"), message);
        assertFalse(message.contains(API_KEY), message);
        server.verify();
    }

    @Test
    void aTransportFailureIsLoggedWithoutTheRequestUrl() {
        server.expect(requestTo(containsString(BUILDING_TIER))).andRespond(withServerError());

        assertThrows(ExternalServiceException.class, () -> provider.reverse(LATITUDE, LONGITUDE));

        assertEquals(1, logLines.list.size());
        String message = logLines.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("40.98,29.03"), message);
        assertFalse(message.contains(API_KEY), message);
        assertFalse(message.contains("geocoding.test"), message);
        assertFalse(message.contains("40.982512"), message);
        server.verify();
    }

    @Test
    void theFallbackTierAsksOnlyForAreaLevelResultTypes() {
        server.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));
        server.expect(requestTo(not(containsString("street_address"))))
                .andExpect(requestTo(containsString(AREA_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));

        provider.reverse(LATITUDE, LONGITUDE);

        server.verify();
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(GoogleGeocodingAddressProvider.class);
    }

    private static String results(String... results) {
        return "{\"status\":\"OK\",\"results\":[" + String.join(",", List.of(results)) + "]}";
    }

    private static String streetAddress() {
        return """
                {"types":["street_address"],"address_components":[
                  {"long_name":"İstanbul","short_name":"İstanbul","types":["administrative_area_level_1"]},
                  {"long_name":"12","short_name":"12","types":["street_number"]},
                  {"long_name":"Türkiye","short_name":"TR","types":["country"]},
                  {"long_name":"Kadıköy","short_name":"Kadıköy","types":["administrative_area_level_2"]},
                  {"long_name":"Moda Caddesi","short_name":"Moda Cd.","types":["route"]},
                  {"long_name":"Caferağa Mahallesi","short_name":"Caferağa","types":["neighborhood"]},
                  {"long_name":"34710","short_name":"34710","types":["postal_code"]}
                ]}""";
    }

    private static String routeOnly() {
        return """
                {"types":["route"],"address_components":[
                  {"long_name":"İstanbul","short_name":"İstanbul","types":["administrative_area_level_1"]},
                  {"long_name":"Türkiye","short_name":"TR","types":["country"]},
                  {"long_name":"Kadıköy","short_name":"Kadıköy","types":["administrative_area_level_2"]},
                  {"long_name":"Moda Caddesi","short_name":"Moda Cd.","types":["route"]},
                  {"long_name":"Caferağa Mahallesi","short_name":"Caferağa","types":["neighborhood"]},
                  {"long_name":"34710","short_name":"34710","types":["postal_code"]}
                ]}""";
    }

    private static String neighborhoodOnly() {
        return """
                {"types":["neighborhood","political"],"address_components":[
                  {"long_name":"İstanbul","short_name":"İstanbul","types":["administrative_area_level_1"]},
                  {"long_name":"Türkiye","short_name":"TR","types":["country"]},
                  {"long_name":"Kadıköy","short_name":"Kadıköy","types":["administrative_area_level_2"]},
                  {"long_name":"Caferağa Mahallesi","short_name":"Caferağa","types":["neighborhood"]},
                  {"long_name":"34710","short_name":"34710","types":["postal_code"]}
                ]}""";
    }
}
