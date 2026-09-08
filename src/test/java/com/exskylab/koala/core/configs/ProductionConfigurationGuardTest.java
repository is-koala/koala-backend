package com.exskylab.koala.core.configs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.core.properties.CorsProperties;
import com.exskylab.koala.core.properties.DeploymentProperties;
import com.exskylab.koala.core.properties.TrustedProxyProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigurationGuardTest {

    private static final String PRODUCTION = "SERVICE_ENVIRONMENT=production";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(GuardConfiguration.class);

    private ListAppender<ILoggingEvent> appender;

    private Logger guardLogger;

    @BeforeEach
    void captureGuardOutput() {
        appender = new ListAppender<>();
        appender.start();
        guardLogger = (Logger) LoggerFactory.getLogger(ProductionConfigurationGuard.class);
        guardLogger.addAppender(appender);
    }

    @AfterEach
    void releaseGuardOutput() {
        guardLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void aProductionConfigurationThatMatchesTheDecisionsStartsWithoutADriftWarning() {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(List.of(), warnings());
                });
    }

    @Test
    void theGuardReadsServiceEnvironmentThroughItsOwnBindingAndNotThePaymentOne() {
        contextRunner.withPropertyValues(PRODUCTION)
                .run(context -> assertEquals(DeploymentProperties.Deployment.PRODUCTION,
                        context.getBean(DeploymentProperties.class).getDeployment()));

        appender.list.clear();

        contextRunner.withPropertyValues("iyzico.deployment=production")
                .run(context -> {
                    assertEquals(DeploymentProperties.Deployment.LOCAL,
                            context.getBean(DeploymentProperties.class).getDeployment());
                    assertEquals(List.of(), warnings());
                });
    }

    @Test
    void anUnrecognisedEnvironmentNameStopsStartupInsteadOfSilentlyMeaningNotProduction() {
        contextRunner.withPropertyValues("SERVICE_ENVIRONMENT=prod")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void anUnsetEnvironmentIsLocalAndTheGuardStaysSilent() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(DeploymentProperties.Deployment.LOCAL,
                    context.getBean(DeploymentProperties.class).getDeployment());
            assertEquals(List.of(), warnings());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0/0", "::/0", "10.0.0.0/24,0.0.0.0/0"})
    void aTrustedProxyListThatTrustsEveryClientStopsProductionStartup(String proxies) {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9", "APP_TRUSTED_PROXIES=" + proxies)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void aNarrowTrustedProxyListStartsInProduction() {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24,172.18.0.7")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void theCatchAllProxyRuleIsProductionOnly() {
        contextRunner.withPropertyValues("APP_TRUSTED_PROXIES=0.0.0.0/0")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void anEmptyTrustedProxyListWarnsInProductionInsteadOfStoppingTodaysDeployment() {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("APP_TRUSTED_PROXIES"), warnings().toString());
                });
    }

    @Test
    void aPopulatedRestCorsAllowlistWarnsInProduction() {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24",
                        "CORS_ALLOWED_ORIGINS=https://iskoala.com")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("CORS_ALLOWED_ORIGINS"), warnings().toString());
                });

        appender.list.clear();

        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24")
                .run(context -> assertFalse(warnedAbout("CORS_ALLOWED_ORIGINS"), warnings().toString()));
    }

    @Test
    void anEnabledSpringdocWarnsInProduction() {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24", "SPRINGDOC_ENABLED=true")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("SPRINGDOC_ENABLED"), warnings().toString());
                });

        appender.list.clear();

        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24", "SPRINGDOC_ENABLED=false")
                .run(context -> assertFalse(warnedAbout("SPRINGDOC_ENABLED"), warnings().toString()));
    }

    @Test
    void aBlankServiceVersionWarnsInProduction() {
        contextRunner.withPropertyValues(PRODUCTION, "APP_TRUSTED_PROXIES=10.0.0.0/24")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("SERVICE_VERSION"), warnings().toString());
                });
    }

    @Test
    void theEffectiveVersionIsLoggedSoADuplicateDefinitionCanBeCompared() {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24")
                .run(context -> assertTrue(appender.list.stream()
                                .filter(event -> event.getLevel() == Level.INFO)
                                .anyMatch(event -> event.getFormattedMessage().contains("version=0.4.9")),
                        messages().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*",
            "https://*.iskoala.com",
            "https://iskoala.com/",
            "https://iskoala.com/app",
            "iskoala.com",
            "ftp://iskoala.com",
            "https://user@iskoala.com",
            "https://iskoala.com?tenant=web",
            "https://iskoala.com#top"
    })
    void aCorsOriginThatIsNotAnExactOriginStopsStartupInEveryEnvironment(String origin) {
        contextRunner.withPropertyValues("CORS_ALLOWED_ORIGINS=" + origin)
                .run(context -> assertNotNull(context.getStartupFailure()));

        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24", "CORS_ALLOWED_ORIGINS=" + origin)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://iskoala.com", "http://localhost:3000", "https://admin.iskoala.com"})
    void anExactCorsOriginIsAccepted(String origin) {
        contextRunner.withPropertyValues("CORS_ALLOWED_ORIGINS=" + origin).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(List.of(origin), context.getBean(CorsProperties.class).getAllowedOrigins());
        });
    }

    @Test
    void theShippedDefaultIsAnEmptyRestAllowlistAndItStarts() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(List.of(), context.getBean(CorsProperties.class).getAllowedOrigins());
        });
    }

    private boolean warnedAbout(String variable) {
        return warnings().stream().anyMatch(message -> message.contains(variable));
    }

    private List<String> warnings() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({DeploymentProperties.class, CorsProperties.class, TrustedProxyProperties.class})
    @Import(ProductionConfigurationGuard.class)
    static class GuardConfiguration {}
}
