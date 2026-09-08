package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    @AssertTrue(message = "app.cors.allowed-origins accepts exact http(s) origins only: a wildcard, a path, "
            + "a query, a fragment, user info or a missing scheme is rejected at startup")
    public boolean isEveryAllowedOriginAnExactOrigin() {
        return allowedOrigins.stream().allMatch(CorsProperties::isExactOrigin);
    }

    private static boolean isExactOrigin(String value) {
        if (value.contains("*")) {
            return false;
        }

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        String path = uri.getRawPath();
        return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && uri.getHost() != null
                && uri.getRawUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && (path == null || path.isEmpty());
    }
}
