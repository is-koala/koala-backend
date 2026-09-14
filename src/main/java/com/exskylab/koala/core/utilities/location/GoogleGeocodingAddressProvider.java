package com.exskylab.koala.core.utilities.location;

import com.exskylab.koala.core.constants.AddressMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.properties.AddressSearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;

@Slf4j
@Component
public class GoogleGeocodingAddressProvider implements AddressReverseGeocoder {
    static final String BUILDING_RESULT_TYPES = "street_address|premise";
    static final String AREA_RESULT_TYPES = "route|sublocality|neighborhood|postal_code";

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
        Optional<ProviderAddressCandidate> building = lookup(latitude, longitude, BUILDING_RESULT_TYPES);
        if (building.isPresent()) {
            return building;
        }
        return lookup(latitude, longitude, AREA_RESULT_TYPES);
    }

    private Optional<ProviderAddressCandidate> lookup(BigDecimal latitude, BigDecimal longitude,
                                                      String resultTypes) {
        URI url = UriComponentsBuilder.fromUriString(properties.getGeocodingBaseUrl())
                .path("/maps/api/geocode/json")
                .queryParam("latlng", latitude.toPlainString() + "," + longitude.toPlainString())
                .queryParam("language", "tr")
                .queryParam("region", "tr")
                .queryParam("result_type", resultTypes)
                .queryParam("key", properties.getApiKey())
                .build().encode().toUri();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Reverse geocoding returned HTTP {} without a usable body near {}",
                        response.getStatusCode().value(), LogMasks.coordinates(latitude, longitude));
                throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();
            if ("ZERO_RESULTS".equals(status)) {
                return Optional.empty();
            }
            if (!"OK".equals(status) || root.path("results").isEmpty()) {
                log.warn("Reverse geocoding returned status {} for result types {} near {}",
                        status, resultTypes, LogMasks.coordinates(latitude, longitude));
                throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
            }
            return Optional.of(mostDetailed(root.path("results"), latitude, longitude));
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Reverse geocoding call failed with {} near {}",
                    exception.getClass().getSimpleName(), LogMasks.coordinates(latitude, longitude));
            throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
        }
    }

    private ProviderAddressCandidate mostDetailed(JsonNode results, BigDecimal latitude, BigDecimal longitude) {
        ProviderAddressCandidate best = null;
        for (JsonNode result : results) {
            ProviderAddressCandidate candidate = map(result, latitude, longitude);
            if (best == null || candidate.isMoreDetailedThan(best)) {
                best = candidate;
            }
        }
        return best;
    }

    private ProviderAddressCandidate map(JsonNode result, BigDecimal latitude, BigDecimal longitude) {
        return GoogleAddressComponents.of(result.path("address_components"), "long_name", "short_name")
                .toCandidate(latitude, longitude);
    }

    private void ensureConfigured() {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ExternalServiceException(AddressMessages.SEARCH_PROVIDER_UNAVAILABLE);
        }
    }
}
