package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityVerificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IdentityConfiguration.class);

    private final ApplicationContextRunner wiredRunner = contextRunner
            .withInitializer(context -> runtimeYaml()
                    .forEach(source -> context.getEnvironment().getPropertySources().addFirst(source)));

    private static List<PropertySource<?>> runtimeYaml() {
        try {
            return new YamlPropertySourceLoader()
                    .load("application.yaml", new ClassPathResource("application.yaml"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void theShippedDefaultsStartWithBothAuthenticationChecksDisabled() {
        wiredRunner.run(context -> {
            assertNull(context.getStartupFailure());
            IdentityVerificationProperties properties = context.getBean(IdentityVerificationProperties.class);
            assertFalse(properties.isEnabled());
            assertFalse(properties.isActiveAuthenticationRequired());
        });
    }

    @Test
    void activeAuthenticationCannotBeRequiredWhilePassiveAuthenticationIsDisabled() {
        contextRunner.withPropertyValues(
                "identity.passive-authentication.enabled=false",
                "identity.passive-authentication.active-authentication-required=true"
        ).run(context -> assertNotNull(context.getStartupFailure(),
                "Active Authentication zorunluysa Passive Authentication da açık olmalı."));
    }

    @Test
    void passiveAndActiveAuthenticationMayBeEnabledTogether() {
        contextRunner.withPropertyValues(
                "identity.passive-authentication.enabled=true",
                "identity.passive-authentication.active-authentication-required=true"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            IdentityVerificationProperties properties = context.getBean(IdentityVerificationProperties.class);
            assertTrue(properties.isEnabled());
            assertTrue(properties.isActiveAuthenticationRequired());
        });
    }

    @Test
    void theDocumentedEnvironmentVariablesReachTheProperties() {
        wiredRunner.withPropertyValues(
                "IDENTITY_PASSIVE_AUTH_ENABLED=true",
                "IDENTITY_ACTIVE_AUTH_REQUIRED=true",
                "IDENTITY_CSCA_DIRECTORY=/test-only/csca",
                "IDENTITY_CHALLENGE_VALIDITY_MINUTES=17"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            IdentityVerificationProperties properties = context.getBean(IdentityVerificationProperties.class);
            assertTrue(properties.isEnabled());
            assertTrue(properties.isActiveAuthenticationRequired());
            assertEquals("/test-only/csca", properties.getTrustAnchorDirectory());
            assertEquals(17, properties.getChallengeValidityMinutes());
        });
    }

    @Test
    void anInvalidEnvironmentCombinationExplainsTheInvariantWithoutLeakingTheTrustAnchorPath() {
        String sensitivePath = "/sensitive/customer/csca";

        wiredRunner.withPropertyValues(
                "IDENTITY_PASSIVE_AUTH_ENABLED=false",
                "IDENTITY_ACTIVE_AUTH_REQUIRED=true",
                "IDENTITY_CSCA_DIRECTORY=" + sensitivePath
        ).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            assertTrue(trace.toString().contains(
                    "identity.passive-authentication.enabled must be true when active authentication is required"));
            assertFalse(trace.toString().contains(sensitivePath));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IdentityVerificationProperties.class)
    static class IdentityConfiguration {}
}
