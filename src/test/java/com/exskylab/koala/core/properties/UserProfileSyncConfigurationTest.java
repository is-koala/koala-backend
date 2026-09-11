package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserProfileSyncConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SyncConfiguration.class);

    @Test
    void aPublisherConfirmTimeoutBelowTheMillisecondResolutionIsRejected() {
        contextRunner.withPropertyValues(
                "user-profile-sync.dispatch.publisher-confirm-timeout=PT0.000000001S"
        ).run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void aConfirmTimeoutThatCanReachTheClaimLeaseIsRejected() {
        contextRunner.withPropertyValues(
                "user-profile-sync.dispatch.claim-timeout=PT10S",
                "user-profile-sync.dispatch.publisher-confirm-timeout=PT10S"
        ).run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserProfileSyncProperties.class)
    static class SyncConfiguration {}
}
