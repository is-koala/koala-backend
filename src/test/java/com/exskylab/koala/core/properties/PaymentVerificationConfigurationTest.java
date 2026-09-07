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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentVerificationConfigurationTest {

    private final ApplicationContextRunner wiredRunner = new ApplicationContextRunner()
            .withUserConfiguration(VerificationConfiguration.class)
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
    void theRuntimeYamlWiresTheInitiationDeadlineOntoTheProperties() {
        wiredRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(Duration.ofMinutes(15),
                    context.getBean(PaymentVerificationProperties.class).getInitiationDeadline(),
                    "application.yaml'daki initiation-deadline anahtari alana baglanmali; baglanmazsa "
                            + "supurme esigi sessizce alan baslaticisina duser ve anahtar hicbir sey yapmaz.");
        });
    }

    @Test
    void theDocumentedEnvironmentVariableReachesTheInitiationDeadline() {
        wiredRunner.withPropertyValues("IYZICO_PAYMENT_INITIATION_DEADLINE=45m").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(Duration.ofMinutes(45),
                    context.getBean(PaymentVerificationProperties.class).getInitiationDeadline());
        });
    }

    @Test
    void everyKeyInTheRuntimeVerificationBlockReachesTheProperties() {
        wiredRunner.withPropertyValues(
                "IYZICO_PAYMENT_VERIFICATION_ENABLED=false",
                "IYZICO_PAYMENT_VERIFICATION_MAX_ATTEMPTS=3",
                "IYZICO_PAYMENT_INITIAL_RECONCILIATION_DELAY=7m",
                "IYZICO_PAYMENT_INITIATION_DEADLINE=2m",
                "IYZICO_PAYMENT_VERIFICATION_INITIAL_BACKOFF=11s",
                "IYZICO_PAYMENT_VERIFICATION_MAX_BACKOFF=13h",
                "IYZICO_PAYMENT_VERIFICATION_BATCH_SIZE=17"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            PaymentVerificationProperties properties = context.getBean(PaymentVerificationProperties.class);
            assertEquals(false, properties.isEnabled());
            assertEquals(3, properties.getMaxAttempts());
            assertEquals(Duration.ofMinutes(7), properties.getInitialReconciliationDelay());
            assertEquals(Duration.ofMinutes(2), properties.getInitiationDeadline());
            assertEquals(Duration.ofSeconds(11), properties.getInitialBackoff());
            assertEquals(Duration.ofHours(13), properties.getMaxBackoff());
            assertEquals(17, properties.getBatchSize());
        });
    }

    @Test
    void theRunbookNamesTheKeyAnOperatorWouldChange() throws IOException {
        String runbook = Files.readString(Path.of("docs/runbooks/payments.md"), StandardCharsets.UTF_8);

        assertTrue(runbook.contains("iyzico.payment-verification.initiation-deadline"),
                "Runbook, supurme esigini degistirecek operatore anahtarin adini vermeli.");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentVerificationProperties.class)
    static class VerificationConfiguration {}
}
