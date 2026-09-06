package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@ConfigurationProperties(prefix = "realtime.origins")
public class StompOriginProperties {

    private String production = "";
    private String staging = "";
    private String development = "";

    public List<String> allowedOrigins() {
        return Stream.of(production, staging, development)
                .map(StompOriginProperties::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public boolean allows(String origin) {
        return origin == null || allowedOrigins().contains(normalize(origin));
    }

    public void setProduction(String production) {
        this.production = production;
    }

    public void setStaging(String staging) {
        this.staging = staging;
    }

    public void setDevelopment(String development) {
        this.development = development;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        URI uri = URI.create(candidate);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getPath() != null && !uri.getPath().isEmpty()
                || uri.getQuery() != null || uri.getFragment() != null || candidate.contains("*")) {
            throw new IllegalArgumentException("STOMP origin tam bir http(s) origin olmali.");
        }
        return candidate;
    }
}
