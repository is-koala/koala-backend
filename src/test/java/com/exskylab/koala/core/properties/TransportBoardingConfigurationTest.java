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
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportBoardingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BoardingConfiguration.class);

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
    void theShippedYamlKeepsTheBoardingNoticesOffAndWiresEveryKey() {
        wiredRunner.run(context -> {
            assertNull(context.getStartupFailure());
            TransportBoardingProperties boarding = context.getBean(TransportBoardingProperties.class);
            TransportReminderProperties reminders = context.getBean(TransportReminderProperties.class);
            assertFalse(boarding.isNoticesEnabled(),
                    "T2 bildirimleri istemciler QR okutabilene kadar kapalı gelmeli; yoksa ilk deploy bildirim yağdırır");
            assertEquals(Duration.ofMinutes(10), boarding.getTokenTtl());
            assertEquals(250, boarding.getFarFromStopMeters());
            assertEquals(Duration.ofMinutes(30), boarding.getScheduleTolerance());
            assertEquals(Duration.ofMinutes(2), boarding.getClockSkew());
            assertEquals(Duration.ofHours(12), boarding.getLateSyncMax());
            assertEquals(15, reminders.getBoardingLeadMinutes());
            assertEquals(10, reminders.getNotBoardedAfterMinutes());
            assertEquals(60, reminders.getNotBoardedLookbackMinutes());
        });
    }

    @Test
    void theDocumentedEnvironmentVariablesReachTheProperties() {
        wiredRunner.withPropertyValues(
                "TRANSPORT_BOARDING_NOTICES_ENABLED=true",
                "TRANSPORT_BOARDING_LATE_SYNC_MAX=PT6H",
                "TRANSPORT_BOARDING_CLOCK_SKEW=PT1M",
                "TRANSPORT_REMINDERS_NOT_BOARDED_LOOKBACK_MINUTES=30"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            TransportBoardingProperties boarding = context.getBean(TransportBoardingProperties.class);
            assertTrue(boarding.isNoticesEnabled());
            assertEquals(Duration.ofHours(6), boarding.getLateSyncMax());
            assertEquals(Duration.ofMinutes(1), boarding.getClockSkew());
            assertEquals(30, context.getBean(TransportReminderProperties.class).getNotBoardedLookbackMinutes());
        });
    }

    @Test
    void aZeroTokenWindowFailsStartup() {
        contextRunner.withPropertyValues("transport.boarding.token-ttl=PT0S")
                .run(context -> assertNotNull(context.getStartupFailure(),
                        "Sıfır pencere her token'ı hemen geçersiz kılar; uygulama açılmamalı."));
    }

    @Test
    void aZeroOrNegativeLateSyncWindowFailsStartup() {
        contextRunner.withPropertyValues("transport.boarding.late-sync-max=PT0S")
                .run(context -> assertNotNull(context.getStartupFailure()));
        contextRunner.withPropertyValues("transport.boarding.late-sync-max=-PT1H")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void negativeToleranceSkewOrDistanceFailStartup() {
        contextRunner.withPropertyValues("transport.boarding.schedule-tolerance=-PT1M")
                .run(context -> assertNotNull(context.getStartupFailure()));
        contextRunner.withPropertyValues("transport.boarding.clock-skew=-PT1M")
                .run(context -> assertNotNull(context.getStartupFailure()));
        contextRunner.withPropertyValues("transport.boarding.far-from-stop-meters=-1")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void reminderWindowsThatCannotSelectAnythingFailStartup() {
        contextRunner.withPropertyValues("transport.reminders.not-boarded-lookback-minutes=0")
                .run(context -> assertNotNull(context.getStartupFailure(),
                        "Sıfır geriye bakış hiçbir kalkışı seçmez; bildirim sessizce ölür."));
        contextRunner.withPropertyValues("transport.reminders.boarding-lead-minutes=0")
                .run(context -> assertNotNull(context.getStartupFailure()));
        contextRunner.withPropertyValues("transport.reminders.not-boarded-after-minutes=-5")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({TransportBoardingProperties.class, TransportReminderProperties.class})
    static class BoardingConfiguration {
    }
}
