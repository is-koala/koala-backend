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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigurationGuardTest {

    private static final String PRODUCTION = "SERVICE_ENVIRONMENT=production";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("CORS_ALLOWED_ORIGINS=")
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
    @ValueSource(strings = {
            "0.0.0.0/0",
            "::/0",
            "10.0.0.0/24,0.0.0.0/0",
            "0.0.0.0/1,128.0.0.0/1",
            "0.0.0.0/2,64.0.0.0/2,128.0.0.0/1",
            "10.0.0.0/24,::/1,8000::/1"
    })
    void aTrustedProxyListThatTrustsEveryClientStopsProductionStartup(String proxies) {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9", "APP_TRUSTED_PROXIES=" + proxies)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10.0.0.0/24,172.18.0.7",
            "0.0.0.0",
            "0.0.0.0/1",
            "0.0.0.0/1,192.0.0.0/2",
            "::/1"
    })
    void aTrustedProxyListThatLeavesAnyClientOutsideItStartsInProduction(String proxies) {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=" + proxies)
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void theRefusedStartupStillWritesTheBaselineSoTheOperatorSeesTheEffectiveValues() {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9", "APP_TRUSTED_PROXIES=0.0.0.0/0")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(appender.list.stream()
                                    .filter(event -> event.getLevel() == Level.INFO)
                                    .anyMatch(event -> event.getFormattedMessage().contains("baseline:")),
                            messages().toString());
                });
    }

    @Test
    void theGuardReadsSpringdocsOwnDefaultInsteadOfAssumingDocumentationIsOff() {
        new ApplicationContextRunner()
                .withUserConfiguration(GuardConfiguration.class)
                .withPropertyValues("app.deployment=production", "app.trusted-proxies=10.0.0.0/24",
                        "logging.structured.ecs.service.version=0.4.9")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("SPRINGDOC_ENABLED"), warnings().toString());
                });
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
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(warnedAbout("SERVICE_VERSION"), warnings().toString());
                });
    }

    @Test
    void anUnsetServiceVersionUsesTheMavenProjectVersion() {
        contextRunner.withPropertyValues(PRODUCTION, "APP_TRUSTED_PROXIES=10.0.0.0/24")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(warnedAbout("SERVICE_VERSION"), warnings().toString());
                    assertTrue(appender.list.stream()
                                    .filter(event -> event.getLevel() == Level.INFO)
                                    .anyMatch(event -> event.getFormattedMessage().contains("version=0.9.6")),
                            messages().toString());
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

    static Stream<String> shapesTheAllowlistRefuses() {
        return Stream.of(
                "*",
                "https://*.iskoala.com",
                "https://iskoala.com//",
                "https://iskoala.com/app",
                "iskoala.com",
                "ftp://iskoala.com",
                "https://user@iskoala.com",
                "https://iskoala.com?tenant=web",
                "https://iskoala.com#top");
    }

    @ParameterizedTest
    @MethodSource("shapesTheAllowlistRefuses")
    void aCorsOriginThatIsNotAnExactOriginStopsStartupInEveryEnvironment(String origin) {
        contextRunner.withPropertyValues("CORS_ALLOWED_ORIGINS=" + origin)
                .run(context -> assertNotNull(context.getStartupFailure()));

        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                        "APP_TRUSTED_PROXIES=10.0.0.0/24", "CORS_ALLOWED_ORIGINS=" + origin)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @MethodSource("shapesTheAllowlistRefuses")
    void aRefusedShapeCannotBeServingABrowserToday(String origin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(List.of(origin));

        for (String browserOrigin : List.of("https://iskoala.com", "https://app.iskoala.com",
                "http://localhost:3000")) {
            try {
                assertNull(configuration.checkOrigin(browserOrigin), origin + " -> " + browserOrigin);
            } catch (IllegalArgumentException everyPreflightThrows) {
                assertEquals("*", origin);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://iskoala.com", "http://localhost:3000", "https://admin.iskoala.com"})
    void anExactCorsOriginIsAccepted(String origin) {
        contextRunner.withPropertyValues("CORS_ALLOWED_ORIGINS=" + origin).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(List.of(origin), context.getBean(CorsProperties.class).getAllowedOrigins());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://iskoala.com/", "https://admin.iskoala.com/"})
    void aTrailingSlashIsNormalisedTheWayTheCorsFilterItselfNormalisesIt(String origin) {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=0.4.9",
                "APP_TRUSTED_PROXIES=10.0.0.0/24", "CORS_ALLOWED_ORIGINS=" + origin).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(List.of(origin.substring(0, origin.length() - 1)),
                    context.getBean(CorsProperties.class).getAllowedOrigins());
        });
    }

    @Test
    void theShippedDefaultIsAnEmptyRestAllowlistAndItStarts() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(List.of(), context.getBean(CorsProperties.class).getAllowedOrigins());
        });
    }

    @Test
    void theShippedDefaultIgnoresCorsConfigurationFromTheDeveloperEnvironment() {
        contextRunner.withSystemProperties("CORS_ALLOWED_ORIGINS=https://must-not-leak.invalid")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(List.of(), context.getBean(CorsProperties.class).getAllowedOrigins());
                });
    }

    @Test
    void everyWarningTheGuardWritesNamesARuleTheCatalogueDeclares() {
        contextRunner.withPropertyValues(PRODUCTION, "SERVICE_VERSION=", "SPRINGDOC_ENABLED=true",
                        "CORS_ALLOWED_ORIGINS=https://iskoala.com")
                .run(context -> {
                    assertNull(context.getStartupFailure());

                    Set<String> declared = Arrays.stream(ProductionConfigurationGuard.Rule.values())
                            .filter(rule -> rule.enforcement() == ProductionConfigurationGuard.Enforcement.WARNS)
                            .map(ProductionConfigurationGuard.Rule::variable)
                            .collect(Collectors.toSet());

                    assertEquals(declared.size(), warnings().size(), warnings().toString());
                    assertEquals(declared, warnings().stream()
                                    .map(ProductionConfigurationGuardTest::variableNamedBy)
                                    .collect(Collectors.toSet()),
                            warnings().toString());
                });
    }

    private static String variableNamedBy(String warning) {
        return warning.substring(warning.indexOf(": ") + 2).split(" ", 2)[0];
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
