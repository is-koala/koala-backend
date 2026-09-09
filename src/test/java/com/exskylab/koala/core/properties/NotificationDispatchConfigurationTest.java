package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationDispatchConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DispatchConfiguration.class);

    @Test
    void aPublisherConfirmTimeoutBelowTheMillisecondResolutionIsRejected() {
        contextRunner.withPropertyValues(
                "notification.dispatch.publisher-confirm-timeout=PT0.000000001S"
        ).run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void aDeliveryClaimTimeoutBelowTheDatabaseTimestampResolutionIsRejected() {
        contextRunner.withPropertyValues(
                "notification.dispatch.delivery-claim-timeout=PT0.000000001S"
        ).run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void aBusyRetryWindowShorterThanTheDeliveryLeaseIsRejected() {
        contextRunner.withPropertyValues(
                "notification.dispatch.delivery-claim-timeout=PT10S",
                "notification.dispatch.provider-timeout=PT1S",
                "notification.dispatch.delivery-busy-retry-interval=PT1S",
                "notification.dispatch.delivery-busy-max-retries=9"
        ).run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void aProviderTimeoutThatCanReachTheDeliveryLeaseIsRejected() {
        contextRunner.withPropertyValues(
                "notification.dispatch.delivery-claim-timeout=PT10S",
                "notification.dispatch.provider-timeout=PT10S"
        ).run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationDispatchProperties.class)
    static class DispatchConfiguration {}
}
