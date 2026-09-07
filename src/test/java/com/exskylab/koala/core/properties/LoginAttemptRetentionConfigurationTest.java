package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptRetentionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RetentionConfiguration.class);

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
    void theShippedDefaultsStartAndKeepTheUnattributedWindowShorter() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            LoginAttemptRetentionProperties properties = context.getBean(LoginAttemptRetentionProperties.class);
            assertEquals(30, properties.getUnattributedDays());
            assertEquals(365, properties.getAttributedDays());
        });
    }

    @Test
    void anUnattributedWindowLongerThanTheAttributedOneFailsStartup() {
        contextRunner.withPropertyValues(
                "login-attempt.retention.unattributed-days=400",
                "login-attempt.retention.attributed-days=30"
        ).run(context -> assertNotNull(context.getStartupFailure(),
                "Sahipsiz pencere bağlı pencereden uzun olamaz; uygulama hiç açılmamalıydı."));
    }

    @Test
    void twoEqualWindowsAreStillAllowed() {
        contextRunner.withPropertyValues(
                "login-attempt.retention.unattributed-days=90",
                "login-attempt.retention.attributed-days=90"
        ).run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void aZeroWindowFailsStartup() {
        contextRunner.withPropertyValues(
                "login-attempt.retention.unattributed-days=0",
                "login-attempt.retention.attributed-days=0"
        ).run(context -> assertNotNull(context.getStartupFailure(),
                "0 gün bugünün satırlarını süpürürdü; uygulama hiç açılmamalıydı."));
    }

    @Test
    void aNegativeWindowFailsStartup() {
        contextRunner.withPropertyValues(
                "login-attempt.retention.unattributed-days=-1",
                "login-attempt.retention.attributed-days=-1"
        ).run(context -> assertNotNull(context.getStartupFailure(),
                "Negatif gün kesimi geleceğe taşıyıp tabloyu boşaltırdı; uygulama hiç açılmamalıydı."));
    }

    @Test
    void aNegativeWindowWouldHaveMovedTheCutoffIntoTheFuture() {
        LoginAttemptRetentionProperties properties = new LoginAttemptRetentionProperties();
        properties.setUnattributedDays(-1);
        Instant now = Instant.parse("2026-09-07T00:00:00Z");

        assertTrue(properties.unattributedCutoff(now).isAfter(now),
                "Doğrulama olmasaydı negatif pencere bütün tabloyu süpürecek bir kesim üretirdi.");
    }

    @Test
    void theCutoffIsTheWindowSubtractedFromTheGivenInstant() {
        LoginAttemptRetentionProperties properties = new LoginAttemptRetentionProperties();
        Instant now = Instant.parse("2026-09-07T00:00:00Z");

        assertEquals(now.minus(30, ChronoUnit.DAYS), properties.unattributedCutoff(now));
        assertEquals(now.minus(365, ChronoUnit.DAYS), properties.attributedCutoff(now));
    }

    @Test
    void theRuntimeYamlWiresEveryKeyOntoTheProperties() {
        wiredRunner.run(context -> {
            assertNull(context.getStartupFailure());
            LoginAttemptRetentionProperties properties = context.getBean(LoginAttemptRetentionProperties.class);
            assertTrue(properties.isEnabled(),
                    "application.yaml işi açık getiriyor; testlerdeki kapalı bayrak bir test ayarı.");
            assertEquals(365, properties.getAttributedDays());
            assertEquals(30, properties.getUnattributedDays());
            assertEquals("0 50 3 * * *",
                    context.getEnvironment().getProperty("login-attempt.retention.cron"));
        });
    }

    @Test
    void theDocumentedEnvironmentVariablesReachTheProperties() {
        wiredRunner.withPropertyValues(
                "LOGIN_ATTEMPT_RETENTION_ENABLED=false",
                "LOGIN_ATTEMPT_RETENTION_ATTRIBUTED_DAYS=200",
                "LOGIN_ATTEMPT_RETENTION_UNATTRIBUTED_DAYS=10"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            LoginAttemptRetentionProperties properties = context.getBean(LoginAttemptRetentionProperties.class);
            assertFalse(properties.isEnabled());
            assertEquals(200, properties.getAttributedDays());
            assertEquals(10, properties.getUnattributedDays());
        });
    }

    @Test
    void aReversedOrderComingFromTheEnvironmentAlsoFailsStartup() {
        wiredRunner.withPropertyValues(
                "LOGIN_ATTEMPT_RETENTION_ATTRIBUTED_DAYS=30",
                "LOGIN_ATTEMPT_RETENTION_UNATTRIBUTED_DAYS=400"
        ).run(context -> assertNotNull(context.getStartupFailure(),
                "Sıra ortam değişkenlerinden gelse de zorlanmalı."));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoginAttemptRetentionProperties.class)
    static class RetentionConfiguration {}
}
