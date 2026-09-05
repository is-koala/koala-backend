package com.exskylab.koala.core.utilities.location;

import com.exskylab.koala.core.constants.AddressMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.properties.AddressSearchProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class GoogleGeocodingAddressProvider implements AddressReverseGeocoder {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AddressSearchProperties properties;

    public GoogleGeocodingAddressProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
                                          AddressSearchProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<ProviderAddressCandidate> reverse(BigDecimal latitude, BigDecimal longitude) {
        ensureConfigured();
        String url = UriComponentsBuilder.fromUriString(properties.getGeocodingBaseUrl())
                .path("/maps/api/geocode/json")
                .queryParam("latlng", latitude.toPlainString() + "," + longitude.toPlainString())
                .queryParam("language", "tr")
                .queryParam("region", "tr")
                .queryParam("result_type", "street_address|premise")
                .queryParam("key", properties.getApiKey())
                .build().encode().toUriString();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();
            if ("ZERO_RESULTS".equals(status)) {
                return Optional.empty();
            }
            if (!"OK".equals(status) || root.path("results").isEmpty()) {
                throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
            }
            return Optional.of(map(root.path("results").get(0), latitude, longitude));
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
        }
    }

    private ProviderAddressCandidate map(JsonNode result, BigDecimal latitude, BigDecimal longitude) {
        Map<String, String> longComponents = new HashMap<>();
        Map<String, String> shortComponents = new HashMap<>();
        for (JsonNode component : result.path("address_components")) {
            String longText = text(component, "long_name");
            String shortText = text(component, "short_name");
            for (JsonNode type : component.path("types")) {
                if (longText != null) longComponents.putIfAbsent(type.asText(), longText);
                if (shortText != null) shortComponents.putIfAbsent(type.asText(), shortText);
            }
        }
        return new ProviderAddressCandidate(
                first(shortComponents, "country"),
                first(longComponents, "administrative_area_level_1", "locality"),
                first(longComponents, "administrative_area_level_2", "sublocality_level_1"),
                first(longComponents, "neighborhood", "sublocality_level_2", "sublocality_level_3"),
                first(longComponents, "route"),
                first(longComponents, "premise"),
                first(longComponents, "street_number"),
                first(longComponents, "postal_code"),
                latitude, longitude);
    }

    private void ensureConfigured() {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
        }
    }

    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
