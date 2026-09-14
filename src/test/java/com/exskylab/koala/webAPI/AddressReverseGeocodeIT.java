package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.properties.AddressSearchProperties;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AddressReverseGeocodeIT extends AbstractIntegrationTest {

    private static final String BUILDING_TIER = "result_type=street_address%7Cpremise";
    private static final String AREA_TIER = "result_type=route%7Csublocality%7Cneighborhood%7Cpostal_code";
    private static final String ZERO_RESULTS = "{\"status\":\"ZERO_RESULTS\",\"results\":[]}";
    private static final String MODA_PIN = "{\"latitude\":40.982512,\"longitude\":29.025411}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AddressSearchProperties properties;

    private final UUID userId = UUID.randomUUID();
    private MockRestServiceServer geocoding;
    private ClientHttpRequestFactory originalRequestFactory;
    private boolean originalEnabled;
    private String originalApiKey;
    private String originalGeocodingBaseUrl;

    @BeforeEach
    void setUp() {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)",
                userId, "reverse-" + userId + "@example.com");
        originalRequestFactory = restTemplate.getRequestFactory();
        originalEnabled = properties.isEnabled();
        originalApiKey = properties.getApiKey();
        originalGeocodingBaseUrl = properties.getGeocodingBaseUrl();
        properties.setEnabled(true);
        properties.setApiKey("integration-test-key");
        properties.setGeocodingBaseUrl("https://geocoding.test");
        geocoding = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void tearDown() {
        if (originalRequestFactory != null) {
            restTemplate.setRequestFactory(originalRequestFactory);
            properties.setEnabled(originalEnabled);
            properties.setApiKey(originalApiKey);
            properties.setGeocodingBaseUrl(originalGeocodingBaseUrl);
        }
        jdbc.update("delete from users where id = ?", userId);
    }

    @Test
    void aBuildingLevelHitAnswersFromTheFirstTierAlone() throws Exception {
        geocoding.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(results(streetAddress()), MediaType.APPLICATION_JSON));

        reverse(MODA_PIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.precision").value("BUILDING"))
                .andExpect(jsonPath("$.data.districtName").value("KADIKÖY"))
                .andExpect(jsonPath("$.data.components.buildingNumber").value("12"))
                .andExpect(jsonPath("$.data.needsUserReview").value(false))
                .andExpect(jsonPath("$.data.candidateToken").isNotEmpty());
        geocoding.verify();
    }

    @Test
    void aPinWithoutABuildingFallsBackToTheStreetTierAndAsksForTheDoorNumber() throws Exception {
        geocoding.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));
        geocoding.expect(requestTo(containsString(AREA_TIER)))
                .andRespond(withSuccess(results(routeOnly()), MediaType.APPLICATION_JSON));

        reverse(MODA_PIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.precision").value("STREET"))
                .andExpect(jsonPath("$.data.districtName").value("KADIKÖY"))
                .andExpect(jsonPath("$.data.components.thoroughfare").value("Moda Caddesi"))
                .andExpect(jsonPath("$.data.components.neighborhood").value("Caferağa Mahallesi"))
                .andExpect(jsonPath("$.data.components.postalCode").value("34710"))
                .andExpect(jsonPath("$.data.components.buildingNumber").doesNotExist())
                .andExpect(jsonPath("$.data.needsUserReview").value(true))
                .andExpect(jsonPath("$.data.position.source").value("REVERSE_GEOCODE_CONFIRMED"));
        geocoding.verify();
    }

    @Test
    void aPinKnownOnlyByNeighborhoodStillYieldsACandidate() throws Exception {
        geocoding.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));
        geocoding.expect(requestTo(containsString(AREA_TIER)))
                .andRespond(withSuccess(results(neighborhoodOnly()), MediaType.APPLICATION_JSON));

        reverse(MODA_PIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.precision").value("NEIGHBORHOOD"))
                .andExpect(jsonPath("$.data.components.neighborhood").value("Caferağa Mahallesi"))
                .andExpect(jsonPath("$.data.components.thoroughfare").value(""))
                .andExpect(jsonPath("$.data.needsUserReview").value(true));
        geocoding.verify();
    }

    @Test
    void aPinWithNothingInEitherTierIsStillNoResult() throws Exception {
        geocoding.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));
        geocoding.expect(requestTo(containsString(AREA_TIER)))
                .andRespond(withSuccess(ZERO_RESULTS, MediaType.APPLICATION_JSON));

        reverse(MODA_PIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_REVERSE_NORESULT"));
        geocoding.verify();
    }

    @Test
    void aDeniedProviderRequestIsAGatewayFailureNotANoResult() throws Exception {
        geocoding.expect(requestTo(containsString(BUILDING_TIER)))
                .andRespond(withSuccess("{\"status\":\"REQUEST_DENIED\",\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        reverse(MODA_PIN)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_SEARCHPROVIDER_UNAVAILABLE"));
        geocoding.verify();
    }

    private ResultActions reverse(String body) throws Exception {
        return mockMvc.perform(post("/locations/address-candidates/reverse")
                .with(user(new UserPrincipal(userId, "reverse-" + userId + "@example.com",
                        Set.of(Role.ROLE_USER), null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String results(String result) {
        return "{\"status\":\"OK\",\"results\":[" + result + "]}";
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
