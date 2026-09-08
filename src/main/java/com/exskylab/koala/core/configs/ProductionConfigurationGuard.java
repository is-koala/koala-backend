package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.CorsProperties;
import com.exskylab.koala.core.properties.DeploymentProperties;
import com.exskylab.koala.core.properties.TrustedProxyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ProductionConfigurationGuard implements InitializingBean {

    public static final String DRIFT_MARKER = "PRODUCTION_CONFIG_DRIFT";

    private static final String SERVICE_VERSION_PROPERTY = "logging.structured.ecs.service.version";

    private static final String API_DOCS_PROPERTY = "springdoc.api-docs.enabled";

    private static final String SWAGGER_UI_PROPERTY = "springdoc.swagger-ui.enabled";

    private final DeploymentProperties deploymentProperties;

    private final CorsProperties corsProperties;

    private final TrustedProxyProperties trustedProxyProperties;

    private final Environment environment;

    public ProductionConfigurationGuard(DeploymentProperties deploymentProperties,
                                        CorsProperties corsProperties,
                                        TrustedProxyProperties trustedProxyProperties,
                                        Environment environment) {
        this.deploymentProperties = deploymentProperties;
        this.corsProperties = corsProperties;
        this.trustedProxyProperties = trustedProxyProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!deploymentProperties.isProduction()) {
            return;
        }

        refuseAProxyListThatTrustsEveryClient();
        reportEffectiveConfiguration();
        reportDrift();
    }

    private void refuseAProxyListThatTrustsEveryClient() {
        List<String> catchAll = configuredProxies().stream()
                .filter(ProductionConfigurationGuard::trustsEveryClient)
                .toList();

        if (!catchAll.isEmpty()) {
            throw new IllegalStateException("app.trusted-proxies must not trust every client in production: "
                    + catchAll.size() + " entry with a zero-length prefix would let any caller set its own "
                    + "Forwarded / X-Forwarded-For value, which defeats the rate limiter and forges the access log");
        }
    }

    private void reportEffectiveConfiguration() {
        log.info("{} baseline: corsOrigins={} trustedProxies={} apiDocsEnabled={} swaggerUiEnabled={} version={}",
                DRIFT_MARKER,
                corsProperties.getAllowedOrigins().size(),
                configuredProxies().size(),
                isEnabled(API_DOCS_PROPERTY),
                isEnabled(SWAGGER_UI_PROPERTY),
                serviceVersion());
    }

    private void reportDrift() {
        if (!corsProperties.getAllowedOrigins().isEmpty()) {
            log.warn("{}: CORS_ALLOWED_ORIGINS carries {} origin in production, ADR-0011 requires an empty REST "
                            + "allowlist because the browser reaches the API only through the web BFF",
                    DRIFT_MARKER, corsProperties.getAllowedOrigins().size());
        }

        if (configuredProxies().isEmpty()) {
            log.warn("{}: APP_TRUSTED_PROXIES is empty in production, so Forwarded and X-Forwarded-For are "
                            + "ignored and every request behind the web BFF will be counted as one client",
                    DRIFT_MARKER);
        }

        if (isEnabled(API_DOCS_PROPERTY) || isEnabled(SWAGGER_UI_PROPERTY)) {
            log.warn("{}: SPRINGDOC_ENABLED is on in production, so every endpoint and DTO schema is publicly "
                    + "readable", DRIFT_MARKER);
        }

        if (serviceVersion().isBlank()) {
            log.warn("{}: SERVICE_VERSION is empty in production, so no log line says which build is running",
                    DRIFT_MARKER);
        }
    }

    private List<String> configuredProxies() {
        return trustedProxyProperties.getTrustedProxies().stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private boolean isEnabled(String property) {
        return Boolean.TRUE.equals(environment.getProperty(property, Boolean.class, Boolean.FALSE));
    }

    private String serviceVersion() {
        String version = environment.getProperty(SERVICE_VERSION_PROPERTY);
        return version == null ? "" : version.trim();
    }

    private static boolean trustsEveryClient(String value) {
        String[] parts = value.trim().split("/", 2);
        if (parts.length != 2) {
            return false;
        }
        try {
            return Integer.parseInt(parts[1].trim()) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
