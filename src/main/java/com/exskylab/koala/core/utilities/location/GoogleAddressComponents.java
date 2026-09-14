package com.exskylab.koala.core.utilities.location;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

final class GoogleAddressComponents {
    private final Map<String, String> longNames = new HashMap<>();
    private final Map<String, String> shortNames = new HashMap<>();

    private GoogleAddressComponents() {
    }

    static GoogleAddressComponents of(JsonNode components, String longField, String shortField) {
        GoogleAddressComponents collected = new GoogleAddressComponents();
        for (JsonNode component : components) {
            String longText = text(component, longField);
            String shortText = text(component, shortField);
            for (JsonNode type : component.path("types")) {
                String key = type.asText();
                if (longText != null) {
                    collected.longNames.putIfAbsent(key, longText);
                }
                if (shortText != null) {
                    collected.shortNames.putIfAbsent(key, shortText);
                }
            }
        }
        return collected;
    }

    ProviderAddressCandidate toCandidate(BigDecimal latitude, BigDecimal longitude) {
        String district = first(longNames, "administrative_area_level_2", "sublocality_level_1");
        return new ProviderAddressCandidate(
                first(shortNames, "country"),
                first(longNames, "administrative_area_level_1", "locality"),
                district,
                neighborhood(district),
                first(longNames, "route"),
                first(longNames, "premise"),
                first(longNames, "street_number"),
                first(longNames, "postal_code"),
                latitude, longitude);
    }

    private String neighborhood(String district) {
        String named = first(longNames, "neighborhood", "administrative_area_level_4",
                "sublocality_level_2", "sublocality_level_3");
        if (named != null) {
            return named;
        }
        String sublocality = first(longNames, "sublocality_level_1");
        if (sublocality == null
                || IstanbulServiceArea.canonicalName(sublocality).equals(IstanbulServiceArea.canonicalName(district))) {
            return null;
        }
        return sublocality;
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
