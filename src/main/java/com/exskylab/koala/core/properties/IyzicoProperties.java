package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Validated
@ConfigurationProperties(prefix = "iyzico")
@Getter
@Setter
public class IyzicoProperties {

    public enum Deployment { LOCAL, STAGING, PRODUCTION }

    public enum ProviderEnvironment { SANDBOX, LIVE, LOOPBACK, UNKNOWN }

    public static final String SANDBOX_BASE_URL = "https://sandbox-api.iyzipay.com";

    public static final String LIVE_BASE_URL = "https://api.iyzipay.com";

    private static final String SANDBOX_HOST = "sandbox-api.iyzipay.com";

    private static final String LIVE_HOST = "api.iyzipay.com";

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private static final Map<Deployment, Set<ProviderEnvironment>> ALLOWED = Map.of(
            Deployment.LOCAL, Set.of(ProviderEnvironment.SANDBOX, ProviderEnvironment.LOOPBACK),
            Deployment.STAGING, Set.of(ProviderEnvironment.SANDBOX),
            Deployment.PRODUCTION, Set.of(ProviderEnvironment.LIVE));

    private String apiKey;

    private String secretKey;

    private String baseUrl = SANDBOX_BASE_URL;

    private Deployment deployment = Deployment.LOCAL;

    private boolean settlementEnabled = true;

    private boolean forceThreeDSecure = true;

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment == null ? Deployment.LOCAL : deployment;
    }

    public boolean isSettlementEnabled() {
        return settlementEnabled;
    }

    public void setSettlementEnabled(boolean settlementEnabled) {
        this.settlementEnabled = settlementEnabled;
    }

    public ProviderEnvironment providerEnvironment() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return ProviderEnvironment.UNKNOWN;
        }

        URI uri;
        try {
            uri = URI.create(baseUrl.strip());
        } catch (IllegalArgumentException exception) {
            return ProviderEnvironment.UNKNOWN;
        }

        String host = uri.getHost();
        if (host == null) {
            return ProviderEnvironment.UNKNOWN;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String normalizedHost = host.toLowerCase(Locale.ROOT);

        if (LOOPBACK_HOSTS.contains(normalizedHost)) {
            return isPlainOrigin(uri) && ("http".equals(scheme) || "https".equals(scheme))
                    ? ProviderEnvironment.LOOPBACK : ProviderEnvironment.UNKNOWN;
        }

        if (!"https".equals(scheme) || !isPlainOrigin(uri) || uri.getPort() != -1) {
            return ProviderEnvironment.UNKNOWN;
        }

        if (SANDBOX_HOST.equals(normalizedHost)) {
            return ProviderEnvironment.SANDBOX;
        }

        if (LIVE_HOST.equals(normalizedHost)) {
            return ProviderEnvironment.LIVE;
        }

        return ProviderEnvironment.UNKNOWN;
    }

    private boolean isPlainOrigin(URI uri) {
        String path = uri.getRawPath();
        return uri.getRawUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && (path == null || path.isEmpty());
    }

    @AssertTrue(message = "iyzico.base-url must be the iyzico environment that iyzico.deployment allows: "
            + "PRODUCTION only with https://api.iyzipay.com, STAGING only with https://sandbox-api.iyzipay.com, "
            + "LOCAL with the sandbox or a loopback stub")
    public boolean isProviderEnvironmentAllowedForDeployment() {
        return ALLOWED.getOrDefault(deployment, Set.of()).contains(providerEnvironment());
    }
}
