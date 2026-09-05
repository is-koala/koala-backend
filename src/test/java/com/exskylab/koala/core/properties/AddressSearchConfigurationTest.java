package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressSearchConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SearchConfiguration.class);

    @Test
    void disabledSearchNeedsNoProviderSecret() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.getBean(AddressSearchProperties.class).isEnabled());
        });
    }

    @Test
    void enabledSearchWithoutProviderSecretFailsStartup() {
        contextRunner.withPropertyValues("address-search.enabled=true").run(context ->
                assertNotNull(context.getStartupFailure()));
    }

    @Test
    void enabledSearchWithProviderSecretStarts() {
        contextRunner.withPropertyValues(
                "address-search.enabled=true",
                "address-search.api-key=test-server-key"
        ).run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBean(AddressSearchProperties.class).isEnabled());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AddressSearchProperties.class)
    static class SearchConfiguration {}
}
