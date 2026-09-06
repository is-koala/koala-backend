package com.exskylab.koala.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class IntegrationTestDatabasePoolConfigurationTest {

    @Test
    void sharedPostgresContainerUsesABoundedConnectionPoolPerContext() {
        Map<String, Supplier<Object>> properties = new HashMap<>();

        AbstractIntegrationTest.datasourceProperties(properties::put);

        Supplier<Object> maximumPoolSize = properties.get("spring.datasource.hikari.maximum-pool-size");
        assertNotNull(maximumPoolSize);
        assertEquals(2, maximumPoolSize.get());

        Supplier<Object> minimumIdle = properties.get("spring.datasource.hikari.minimum-idle");
        assertNotNull(minimumIdle);
        assertEquals(0, minimumIdle.get());
    }
}
