package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.AdminBootstrapProperties;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestPropertySource(properties = "ADMIN_BOOTSTRAP_EMAIL=bootstrap-startup-yok@koala.test")
class AdminBootstrapStartupIT extends AbstractIntegrationTest {

    @Autowired
    private AdminBootstrapRunner runner;

    @Autowired
    private AdminBootstrapProperties properties;

    @Test
    void theApplicationStartsEvenWhenTheConfiguredAccountDoesNotExist() {
        assertNotNull(runner, "Bootstrap runner acilista calisti ve context ayakta kaldi.");
    }

    @Test
    void theEnvironmentVariableReachesTheRunnerThroughApplicationYaml() {
        assertEquals("bootstrap-startup-yok@koala.test", properties.getEmail());
    }
}
