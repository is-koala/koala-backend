package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailDeliveryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DeliveryConfiguration.class);

    @Test
    void missingConfigurationDoesNotAllowAnyRecipient() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.getBean(EmailDeliveryProperties.class).allows("someone@example.test"));
        });
    }

    @Test
    void anExplicitProductionModeCanUseTheUnrestrictedTransport() {
        contextRunner.withPropertyValues("email.delivery.mode=UNRESTRICTED").run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBean(EmailDeliveryProperties.class).allows("someone@example.test"));
        });
    }

    @Test
    void anUnknownDeliveryModeFailsStartup() {
        contextRunner.withPropertyValues("email.delivery.mode=TYPO").run(context ->
                assertNotNull(context.getStartupFailure()));
    }

    @Test
    void wildcardRecipientConfigurationIsRejected() {
        contextRunner.withPropertyValues("email.delivery.allowed-recipients=*").run(context ->
                assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailDeliveryProperties.class)
    static class DeliveryConfiguration {}
}
