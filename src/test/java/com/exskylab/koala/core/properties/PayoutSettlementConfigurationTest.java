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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutSettlementConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SettlementConfiguration.class);

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
    void theShippedDefaultGivesTheStatementThreeDaysToArrive() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(Duration.ofDays(3),
                    context.getBean(PayoutSettlementProperties.class).getOverdueAfter());
        });
    }

    @Test
    void aZeroWindowFailsStartup() {
        contextRunner.withPropertyValues("payout.settlement.overdue-after=0s")
                .run(context -> assertNotNull(context.getStartupFailure(),
                        "Sifir pencere her PROCESSING satirini vadesi gecmis sayardi; acilis dusmeliydi."));
    }

    @Test
    void aNegativeWindowFailsStartup() {
        contextRunner.withPropertyValues("payout.settlement.overdue-after=-1d")
                .run(context -> assertNotNull(context.getStartupFailure(),
                        "Negatif pencere kesimi gelecege tasir ve alarmi surekli yakardi."));
    }

    @Test
    void theRuntimeYamlWiresEveryKeyOntoTheProperties() {
        wiredRunner.run(context -> {
            assertNull(context.getStartupFailure());
            PayoutSettlementProperties properties = context.getBean(PayoutSettlementProperties.class);
            assertTrue(properties.isOverdueMonitoringEnabled(),
                    "application.yaml izlemeyi acik getiriyor; testlerdeki kapali bayrak bir test ayari.");
            assertEquals(Duration.ofDays(3), properties.getOverdueAfter());
            assertEquals(Duration.ofDays(10), properties.getStalledAfter());
            assertEquals("0 20 * * * *",
                    context.getEnvironment().getProperty("payout.settlement.overdue-cron"));
        });
    }

    @Test
    void aSecondTierThatIsNotBeyondTheFirstFailsStartup() {
        contextRunner.withPropertyValues("payout.settlement.stalled-after=3d")
                .run(context -> assertNotNull(context.getStartupFailure(),
                        "Ilk kademeyle ayni esik ikinci bir alarmi hicbir sey soylemeden atesler."));
        contextRunner.withPropertyValues("payout.settlement.stalled-after=1d")
                .run(context -> assertNotNull(context.getStartupFailure(),
                        "Ilk kademeden once yanan bir yukseltme kademesi sirayi tersine cevirirdi."));
    }

    @Test
    void theDocumentedEnvironmentVariablesReachTheProperties() {
        wiredRunner.withPropertyValues(
                "PAYOUT_SETTLEMENT_OVERDUE_MONITORING_ENABLED=false",
                "PAYOUT_SETTLEMENT_OVERDUE_AFTER=7d"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            PayoutSettlementProperties properties = context.getBean(PayoutSettlementProperties.class);
            assertFalse(properties.isOverdueMonitoringEnabled());
            assertEquals(Duration.ofDays(7), properties.getOverdueAfter());
        });
    }

    @Test
    void theRunbookQueueUsesTheSameThresholdAsTheAlarm() throws IOException {
        PayoutSettlementProperties shipped = new PayoutSettlementProperties();
        String runbook = Files.readString(Path.of("docs/runbooks/payments.md"), StandardCharsets.UTF_8);

        assertTrue(runbook.contains("interval '" + shipped.getOverdueAfter().toDays() + " days'"),
                "Runbook kuyrugu payout.settlement.overdue-after ile ayni esigi kullanmali; ayrisirsa "
                        + "alarm gelir ama operatorun kosacagi sorgu bos doner. Varsayilan="
                        + shipped.getOverdueAfter());
        assertTrue(runbook.contains("varsayılan " + shipped.getStalledAfter().toDays() + " gün"),
                "Ikinci kademenin esigi de runbook'ta yazili olmali; operator hangi satirin yukseltildigini "
                        + "aksi halde bilemez. Varsayilan=" + shipped.getStalledAfter());
    }

    @Test
    void theRunbookQueueMeasuresTheWaitFromTheProviderAcceptanceNotTheRelease() throws IOException {
        String runbook = Files.readString(Path.of("docs/runbooks/payments.md"), StandardCharsets.UTF_8);
        String queue = runbook.substring(runbook.indexOf("select id, job_assignment_id, provider_reference"));
        queue = queue.substring(0, queue.indexOf("```"));

        assertTrue(queue.contains("processing_at <"),
                "Bekleyis PROCESSING'e gecisle baslar; created_at uzerinden sayan bir sorgu outbox'ta "
                        + "bekleyen bir satiri gunlerce beklemis gibi gosterir: " + queue);
        assertFalse(queue.contains("created_at"),
                "created_at hakedisin acildigi andir, banka kanitini beklemeye basladigi an degil: " + queue);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PayoutSettlementProperties.class)
    static class SettlementConfiguration {}
}
