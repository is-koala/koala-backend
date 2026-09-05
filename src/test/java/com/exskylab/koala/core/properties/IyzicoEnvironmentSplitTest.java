package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IyzicoEnvironmentSplitTest {

    private static final String LIVE = "https://api.iyzipay.com";
    private static final String SANDBOX = "https://sandbox-api.iyzipay.com";
    private static final String LOOPBACK = "http://localhost:9998";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IyzicoConfiguration.class);

    @Test
    void stagingRefusesToStartAgainstTheLiveProvider() {
        contextRunner.withPropertyValues("iyzico.deployment=staging", "iyzico.base-url=" + LIVE)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void localRefusesToStartAgainstTheLiveProvider() {
        contextRunner.withPropertyValues("iyzico.deployment=local", "iyzico.base-url=" + LIVE)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void productionRefusesToStartAgainstTheSandboxProvider() {
        contextRunner.withPropertyValues("iyzico.deployment=production", "iyzico.base-url=" + SANDBOX)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void productionAcceptsTheLiveProvider() {
        contextRunner.withPropertyValues("iyzico.deployment=production", "iyzico.base-url=" + LIVE)
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void stagingAcceptsTheSandboxProvider() {
        contextRunner.withPropertyValues("iyzico.deployment=staging", "iyzico.base-url=" + SANDBOX)
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void anUnknownProviderHostIsRejectedEvenLocally() {
        contextRunner.withPropertyValues("iyzico.deployment=local", "iyzico.base-url=https://api.iyzipay.com.evil.test")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void theLoopbackProviderIsAvailableOnlyLocally() {
        contextRunner.withPropertyValues("iyzico.deployment=local", "iyzico.base-url=" + LOOPBACK)
                .run(context -> assertNull(context.getStartupFailure()));

        contextRunner.withPropertyValues("iyzico.deployment=staging", "iyzico.base-url=" + LOOPBACK)
                .run(context -> assertNotNull(context.getStartupFailure()));

        contextRunner.withPropertyValues("iyzico.deployment=production", "iyzico.base-url=" + LOOPBACK)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void anUnsetDeploymentIsTreatedAsLocalAndStillRefusesLiveKeys() {
        contextRunner.withPropertyValues("iyzico.base-url=" + LIVE)
                .run(context -> assertNotNull(context.getStartupFailure()));

        contextRunner.withPropertyValues("iyzico.deployment=", "iyzico.base-url=" + LIVE)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void anUnrecognisedDeploymentNameFailsStartupInsteadOfPassingForNonProduction() {
        contextRunner.withPropertyValues("iyzico.deployment=prod", "iyzico.base-url=" + LIVE)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void aBlankProviderBaseUrlIsRejected() {
        contextRunner.withPropertyValues("iyzico.deployment=production", "iyzico.base-url=")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void anUnsetServiceEnvironmentResolvesToLocalInTheShippedConfiguration() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(IyzicoConfiguration.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(IyzicoProperties.Deployment.LOCAL,
                            context.getBean(IyzicoProperties.class).getDeployment());
                });
    }

    @Test
    void theShippedProviderDefaultIsTheSandbox() {
        assertEquals(SANDBOX, new IyzicoProperties().getBaseUrl());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IyzicoProperties.class)
    static class IyzicoConfiguration {}
}
