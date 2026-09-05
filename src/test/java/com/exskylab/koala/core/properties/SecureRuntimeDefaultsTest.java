package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecureRuntimeDefaultsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void documentationIsDisabledWhenNoOverrideIsProvided() {
        contextRunner.run(context -> {
            assertEquals("false", context.getEnvironment().getProperty("springdoc.swagger-ui.enabled"));
            assertEquals("false", context.getEnvironment().getProperty("springdoc.api-docs.enabled"));
        });
    }

    @Test
    void documentationCanBeExplicitlyEnabled() {
        contextRunner.withPropertyValues("SPRINGDOC_ENABLED=true").run(context -> {
            assertEquals("true", context.getEnvironment().getProperty("springdoc.swagger-ui.enabled"));
            assertEquals("true", context.getEnvironment().getProperty("springdoc.api-docs.enabled"));
        });
    }

    @Test
    void signedUrlLifetimeDefaultsToThreeMinutes() {
        contextRunner.run(context ->
                assertEquals("3", context.getEnvironment().getProperty("storage.signed-url-minutes")));
    }

    @Test
    void storagePropertyFallbackMatchesTheRuntimeDefault() {
        assertEquals(3, new StorageProperties().getSignedUrlMinutes());
    }
}
