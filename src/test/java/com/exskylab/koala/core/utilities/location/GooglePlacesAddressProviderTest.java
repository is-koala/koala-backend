package com.exskylab.koala.core.utilities.location;

import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.entities.AddressCandidatePrecision;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class GooglePlacesAddressProviderTest {
    private static final String BASE_URL = "https://places.test";

    private MockRestServiceServer server;
    private GooglePlacesAddressProvider provider;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        AddressSearchProperties properties = new AddressSearchProperties();
        properties.setEnabled(true);
        properties.setApiKey("server-restricted-test-key");
        properties.setPlacesBaseUrl(BASE_URL);
        provider = new GooglePlacesAddressProvider(restTemplate, new ObjectMapper(), properties);
    }

    @Test
    void autocompleteIsRestrictedToTurkeyAndKeepsProviderIdsInsideTheAdapter() {
        server.expect(requestTo(BASE_URL + "/v1/places:autocomplete"))
                .andExpect(method(POST))
                .andExpect(header("X-Goog-Api-Key", "server-restricted-test-key"))
                .andExpect(header("X-Goog-FieldMask", containsString("placePrediction.placeId")))
                .andExpect(jsonPath("$.sessionToken").value("session-1"))
                .andExpect(jsonPath("$.includedRegionCodes[0]").value("tr"))
                .andExpect(jsonPath("$.languageCode").value("tr"))
                .andExpect(jsonPath("$.includeQueryPredictions").value(false))
                .andExpect(jsonPath("$.locationRestriction.rectangle.low.latitude").value(40.80))
                .andExpect(jsonPath("$.locationRestriction.rectangle.low.longitude").value(28.55))
                .andExpect(jsonPath("$.locationRestriction.rectangle.high.latitude").value(41.35))
                .andExpect(jsonPath("$.locationRestriction.rectangle.high.longitude").value(29.50))
                .andRespond(withSuccess("""
                        {"suggestions":[{"placePrediction":{
                          "placeId":"provider-place-1",
                          "structuredFormat":{
                            "mainText":{"text":"Moda Caddesi 12"},
                            "secondaryText":{"text":"Kadıköy/İstanbul, Türkiye"}
                          }
                        }}]}
                        """, MediaType.APPLICATION_JSON));

        ProviderAddressSuggestion suggestion = provider.autocomplete("Moda Caddesi", "session-1").getFirst();

        assertEquals("provider-place-1", suggestion.id());
        assertEquals("Moda Caddesi 12", suggestion.mainText());
        assertEquals("Kadıköy/İstanbul, Türkiye", suggestion.secondaryText());
        server.verify();
    }

    @Test
    void detailsAreMappedByComponentTypeInsteadOfProviderArrayOrder() {
        server.expect(requestTo(containsString(BASE_URL + "/v1/places/provider-place-1")))
                .andExpect(requestTo(containsString("languageCode=tr")))
                .andExpect(requestTo(containsString("regionCode=tr")))
                .andExpect(requestTo(containsString("sessionToken=session-1")))
                .andExpect(method(GET))
                .andExpect(header("X-Goog-FieldMask", "id,addressComponents,location"))
                .andRespond(withSuccess("""
                        {
                          "id":"provider-place-1",
                          "addressComponents":[
                            {"longText":"34710","shortText":"34710","types":["postal_code"]},
                            {"longText":"12","shortText":"12","types":["street_number"]},
                            {"longText":"Kadıköy","shortText":"Kadıköy","types":["administrative_area_level_2"]},
                            {"longText":"Türkiye","shortText":"TR","types":["country"]},
                            {"longText":"Moda Caddesi","shortText":"Moda Cd.","types":["route"]},
                            {"longText":"Caferağa Mahallesi","shortText":"Caferağa","types":["neighborhood"]},
                            {"longText":"İstanbul","shortText":"İstanbul","types":["administrative_area_level_1"]}
                          ],
                          "location":{"latitude":40.982512,"longitude":29.025411}
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderAddressCandidate candidate = provider.resolve("provider-place-1", "session-1");

        assertEquals("TR", candidate.countryCode());
        assertEquals("İstanbul", candidate.city());
        assertEquals("Kadıköy", candidate.district());
        assertEquals("Caferağa Mahallesi", candidate.neighborhood());
        assertEquals("Moda Caddesi", candidate.thoroughfare());
        assertEquals("12", candidate.buildingNumber());
        assertEquals("34710", candidate.postalCode());
        assertEquals(new BigDecimal("40.982512"), candidate.latitude());
        assertEquals(new BigDecimal("29.025411"), candidate.longitude());
        server.verify();
    }

    @Test
    void resolveMapsATurkishNeighborhoodDeliveredAsAdministrativeAreaLevel4() {
        server.expect(requestTo(containsString(BASE_URL + "/v1/places/provider-place-2")))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "id":"provider-place-2",
                          "addressComponents":[
                            {"longText":"15","shortText":"15","types":["street_number"]},
                            {"longText":"Okul Caddesi","shortText":"Okul Cd.","types":["route"]},
                            {"longText":"Rumeli Hisarı Mahallesi","shortText":"Rumeli Hisarı Mahallesi","types":["administrative_area_level_4","political"]},
                            {"longText":"Sarıyer","shortText":"Sarıyer","types":["political","sublocality","sublocality_level_1"]},
                            {"longText":"Sarıyer","shortText":"Sarıyer","types":["administrative_area_level_2","political"]},
                            {"longText":"İstanbul","shortText":"İstanbul","types":["administrative_area_level_1","political"]},
                            {"longText":"Türkiye","shortText":"TR","types":["country","political"]},
                            {"longText":"34450","shortText":"34450","types":["postal_code"]}
                          ],
                          "location":{"latitude":41.085123,"longitude":29.055321}
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderAddressCandidate candidate = provider.resolve("provider-place-2", "session-1");

        assertEquals("Sarıyer", candidate.district());
        assertEquals("Rumeli Hisarı Mahallesi", candidate.neighborhood());
        assertEquals("15", candidate.buildingNumber());
        assertEquals(AddressCandidatePrecision.BUILDING, candidate.precision());
        server.verify();
    }

    @Test
    void aDisabledProviderFailsClosedBeforeNetworkAccess() {
        AddressSearchProperties properties = new AddressSearchProperties();
        properties.setEnabled(false);
        GooglePlacesAddressProvider disabled = new GooglePlacesAddressProvider(
                new RestTemplate(), new ObjectMapper(), properties);

        assertThrows(ExternalServiceException.class,
                () -> disabled.autocomplete("Moda Caddesi", "session-1"));
    }
}
