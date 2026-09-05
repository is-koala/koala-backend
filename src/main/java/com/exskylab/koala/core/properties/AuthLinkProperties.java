package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Component
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "auth-links")
public class AuthLinkProperties {

    @NotBlank
    private String publicBaseUrl;

    @NotBlank
    @Pattern(regexp = "koala-dev|koala-staging|koala")
    private String appScheme;

    @AssertTrue(message = "auth-links.public-base-url must be an HTTPS origin or a localhost HTTP origin")
    public boolean isPublicBaseUrlSafe() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(publicBaseUrl);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            boolean local = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            boolean rootPath = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath());
            return (secure || local)
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && rootPath;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
