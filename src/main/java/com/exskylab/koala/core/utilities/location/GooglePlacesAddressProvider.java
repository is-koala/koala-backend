package com.exskylab.koala.core.utilities.location;

import com.exskylab.koala.core.constants.AddressMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.properties.AddressSearchProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GooglePlacesAddressProvider implements AddressSearchProvider {
    private static final String AUTOCOMPLETE_FIELD_MASK =
            "suggestions.placePrediction.placeId,suggestions.placePrediction.structuredFormat.mainText.text," +
                    "suggestions.placePrediction.structuredFormat.secondaryText.text";
    private static final String DETAILS_FIELD_MASK = "id,addressComponents,location";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AddressSearchProperties properties;

    public GooglePlacesAddressProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
                                       AddressSearchProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<ProviderAddressSuggestion> autocomplete(String query, String sessionToken) {
        ensureConfigured();
        Map<String, Object> body = Map.of(
                "input", query,
                "sessionToken", sessionToken,
                "includedRegionCodes", List.of("tr"),
                "languageCode", "tr",
                "regionCode", "tr",
                "includeQueryPredictions", false,
                "locationRestriction", Map.of(
                        "rectangle", Map.of(
                                "low", Map.of("latitude", IstanbulServiceArea.SOUTH,
                                        "longitude", IstanbulServiceArea.WEST),
                                "high", Map.of("latitude", IstanbulServiceArea.NORTH,
                                        "longitude", IstanbulServiceArea.EAST))));
        JsonNode root = call(HttpMethod.POST, "/v1/places:autocomplete", body, AUTOCOMPLETE_FIELD_MASK);
        List<ProviderAddressSuggestion> suggestions = new ArrayList<>();
        for (JsonNode item : root.path("suggestions")) {
            JsonNode prediction = item.path("placePrediction");
            String placeId = GoogleAddressComponents.text(prediction, "placeId");
            if (placeId == null) {
                continue;
            }
            JsonNode format = prediction.path("structuredFormat");
            suggestions.add(new ProviderAddressSuggestion(
                    placeId,
                    GoogleAddressComponents.text(format.path("mainText"), "text"),
                    GoogleAddressComponents.text(format.path("secondaryText"), "text")));
        }
        return suggestions;
    }

    @Override
    public ProviderAddressCandidate resolve(String providerCandidateId, String sessionToken) {
        ensureConfigured();
        String path = UriComponentsBuilder.fromPath("/v1/places/{placeId}")
                .queryParam("languageCode", "tr")
                .queryParam("regionCode", "tr")
                .queryParam("sessionToken", sessionToken)
                .buildAndExpand(providerCandidateId)
                .encode()
                .toUriString();
        JsonNode root = call(HttpMethod.GET, path, null, DETAILS_FIELD_MASK);
        JsonNode location = root.path("location");
        return GoogleAddressComponents.of(root.path("addressComponents"), "longText", "shortText")
                .toCandidate(decimal(location, "latitude"), decimal(location, "longitude"));
    }

    private JsonNode call(HttpMethod method, String path, Object body, String fieldMask) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", properties.getApiKey());
        headers.set("X-Goog-FieldMask", fieldMask);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(properties.getPlacesBaseUrl() + path),
                    method,
                    new HttpEntity<>(body, headers),
                    String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
            }
            return objectMapper.readTree(response.getBody());
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
        }
    }

    private void ensureConfigured() {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
        }
        return value.decimalValue();
    }
}
