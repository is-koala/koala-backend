package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.helpers.IpNetwork;
import com.exskylab.koala.core.properties.CorsProperties;
import com.exskylab.koala.core.properties.DeploymentProperties;
import com.exskylab.koala.core.properties.TrustedProxyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ProductionConfigurationGuard implements InitializingBean {

    public static final String DRIFT_MARKER = "PRODUCTION_CONFIG_DRIFT";

    public enum Enforcement { REFUSES, WARNS }

    public enum Rule {

        SERVICE_ENVIRONMENT_IS_A_KNOWN_NAME("SERVICE_ENVIRONMENT", Enforcement.REFUSES),
        CORS_ALLOWED_ORIGINS_ARE_EXACT("CORS_ALLOWED_ORIGINS", Enforcement.REFUSES),
        TRUSTED_PROXIES_DO_NOT_COVER_EVERY_CLIENT("APP_TRUSTED_PROXIES", Enforcement.REFUSES),
        CORS_ALLOWLIST_IS_EMPTY("CORS_ALLOWED_ORIGINS", Enforcement.WARNS),
        TRUSTED_PROXIES_ARE_DECLARED("APP_TRUSTED_PROXIES", Enforcement.WARNS),
        SPRINGDOC_IS_DISABLED("SPRINGDOC_ENABLED", Enforcement.WARNS),
        SERVICE_VERSION_IS_DECLARED("SERVICE_VERSION", Enforcement.WARNS);

        private final String variable;

        private final Enforcement enforcement;

        Rule(String variable, Enforcement enforcement) {
            this.variable = variable;
            this.enforcement = enforcement;
        }

        public String variable() {
            return variable;
        }

        public Enforcement enforcement() {
            return enforcement;
        }
    }

    private static final String SERVICE_VERSION_PROPERTY = "logging.structured.ecs.service.version";

    private static final String API_DOCS_PROPERTY = "springdoc.api-docs.enabled";

    private static final String SWAGGER_UI_PROPERTY = "springdoc.swagger-ui.enabled";

    private final DeploymentProperties deploymentProperties;

    private final CorsProperties corsProperties;

    private final TrustedProxyProperties trustedProxyProperties;

    private final Environment environment;

    private final boolean apiDocsDefault;

    private final boolean swaggerUiDefault;

    public ProductionConfigurationGuard(DeploymentProperties deploymentProperties,
                                        CorsProperties corsProperties,
                                        TrustedProxyProperties trustedProxyProperties,
                                        Environment environment) {
        this.deploymentProperties = deploymentProperties;
        this.corsProperties = corsProperties;
        this.trustedProxyProperties = trustedProxyProperties;
        this.environment = environment;
        this.apiDocsDefault = springdocDefaultFor(SpringDocConfiguration.class, API_DOCS_PROPERTY);
        this.swaggerUiDefault = springdocDefaultFor(SwaggerConfig.class, SWAGGER_UI_PROPERTY);
    }

    @Override
    public void afterPropertiesSet() {
        if (!deploymentProperties.isProduction()) {
            return;
        }

        reportEffectiveConfiguration();
        reportDrift();
        refuseAProxyListThatTrustsEveryClient();
    }

    private void reportEffectiveConfiguration() {
        log.info("{} baseline: corsOrigins={} trustedProxies={} apiDocsEnabled={} swaggerUiEnabled={} version={}",
                DRIFT_MARKER,
                corsProperties.getAllowedOrigins().size(),
                trustedProxyProperties.getTrustedProxies().size(),
                isEnabled(API_DOCS_PROPERTY, apiDocsDefault),
                isEnabled(SWAGGER_UI_PROPERTY, swaggerUiDefault),
                serviceVersion());
    }

    private void reportDrift() {
        if (!corsProperties.getAllowedOrigins().isEmpty()) {
            warn(Rule.CORS_ALLOWLIST_IS_EMPTY, "carries " + corsProperties.getAllowedOrigins().size()
                    + " origin in production, ADR-0011 requires an empty REST allowlist because the browser "
                    + "reaches the API only through the web BFF");
        }

        if (trustedProxyProperties.getTrustedProxies().isEmpty()) {
            warn(Rule.TRUSTED_PROXIES_ARE_DECLARED, "is empty in production, so Forwarded and X-Forwarded-For "
                    + "are ignored and every request behind the web BFF will be counted as one client");
        }

        if (isEnabled(API_DOCS_PROPERTY, apiDocsDefault) || isEnabled(SWAGGER_UI_PROPERTY, swaggerUiDefault)) {
            warn(Rule.SPRINGDOC_IS_DISABLED, "is on in production, so every endpoint and DTO schema is "
                    + "publicly readable");
        }

        if (serviceVersion().isBlank()) {
            warn(Rule.SERVICE_VERSION_IS_DECLARED, "is empty in production, so no log line says which build "
                    + "is running");
        }
    }

    private void refuseAProxyListThatTrustsEveryClient() {
        List<IpNetwork> networks = trustedProxyProperties.getTrustedProxies().stream()
                .map(IpNetwork::parse)
                .toList();

        if (IpNetwork.coverTheWholeAddressSpace(networks)) {
            throw new IllegalStateException(Rule.TRUSTED_PROXIES_DO_NOT_COVER_EVERY_CLIENT.variable()
                    + " must not trust every client in production: its "
                    + trustedProxyProperties.getTrustedProxies().size() + " entry together covers a whole "
                    + "address family, which would let any caller set its own Forwarded / X-Forwarded-For "
                    + "value, defeat the rate limiter and forge the access log");
        }
    }

    private void warn(Rule rule, String drift) {
        log.warn("{}: {} {}", DRIFT_MARKER, rule.variable(), drift);
    }

    private boolean isEnabled(String property, boolean springdocDefault) {
        return Boolean.TRUE.equals(environment.getProperty(property, Boolean.class, springdocDefault));
    }

    private String serviceVersion() {
        String version = environment.getProperty(SERVICE_VERSION_PROPERTY);
        return version == null ? "" : version.trim();
    }

    private static boolean springdocDefaultFor(Class<?> configuration, String property) {
        ConditionalOnProperty condition =
                AnnotatedElementUtils.findMergedAnnotation(configuration, ConditionalOnProperty.class);
        if (condition == null || !condition.prefix().isEmpty()
                || !List.of(condition.name()).contains(property)) {
            throw new IllegalStateException(configuration.getName() + " no longer declares @ConditionalOnProperty "
                    + "for " + property + ", so the guard cannot read springdoc's own default and would report "
                    + "a documentation surface it has not measured");
        }
        return condition.matchIfMissing();
    }
}
