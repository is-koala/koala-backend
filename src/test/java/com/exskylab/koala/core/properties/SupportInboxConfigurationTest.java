package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SupportInboxConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(InboxConfiguration.class);

    @Test
    void thePublishedControllerAddressIsTheDefaultMailbox() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals("info@iskoala.com", context.getBean(SupportInboxProperties.class).getMailbox());
        });
    }

    @Test
    void aNonEmailMailboxFailsStartup() {
        contextRunner.withPropertyValues("email.support.mailbox=not-an-email").run(context ->
                assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SupportInboxProperties.class)
    static class InboxConfiguration {}
}
