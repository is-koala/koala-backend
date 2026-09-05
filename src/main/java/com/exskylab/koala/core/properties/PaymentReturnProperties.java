package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Component
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "payments")
public class PaymentReturnProperties {

    private static final Map<String, String> APP_SCHEMES = Map.of(
            "koala-dev://", "koala-dev",
            "koala-staging://", "koala-staging",
            "koala://", "koala");

    @NotBlank
    private String returnBaseUrl;

    @AssertTrue(message = "payments.return-base-url must be an HTTPS origin or an allowed Koala app scheme")
    public boolean isReturnBaseUrlSafe() {
        return parseReturnTarget().isPresent();
    }

    public ReturnTarget returnTarget() {
        return parseReturnTarget().orElseThrow(
                () -> new IllegalStateException("payments.return-base-url is not a safe return target"));
    }

    private Optional<ReturnTarget> parseReturnTarget() {
        if (returnBaseUrl == null || returnBaseUrl.isBlank()) {
            return Optional.empty();
        }
        String appScheme = APP_SCHEMES.get(returnBaseUrl);
        if (appScheme != null) {
            return Optional.of(new AppScheme(appScheme));
        }
        try {
            URI uri = URI.create(returnBaseUrl);
            boolean rootPath = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath());
            boolean safe = "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && rootPath;
            return safe ? Optional.of(new WebOrigin(uri)) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public sealed interface ReturnTarget permits AppScheme, WebOrigin {
    }

    public record AppScheme(String value) implements ReturnTarget {
    }

    public record WebOrigin(URI value) implements ReturnTarget {
    }
}
