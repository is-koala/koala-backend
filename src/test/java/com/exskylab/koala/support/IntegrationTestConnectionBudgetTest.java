package com.exskylab.koala.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationTestConnectionBudgetTest {

    private static final Path TEST_PROPERTIES = Path.of("src", "test", "resources", "application.properties");

    private static final int POSTGRES_MAX_CONNECTIONS = 100;
    private static final int SUPERUSER_RESERVED_CONNECTIONS = 3;
    private static final int CACHED_CONTEXT_BUDGET = 20;

    private static Properties testProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(TEST_PROPERTIES)) {
            properties.load(stream);
        }
        return properties;
    }

    @Test
    void theHikariPoolIsCapped() throws IOException {
        String configured = testProperties().getProperty("spring.datasource.hikari.maximum-pool-size");

        assertNotNull(configured,
                "spring.datasource.hikari.maximum-pool-size kaldirilmis; varsayilan 10 ile context basina "
                        + "10 baglanti tutulur (CLAUDE.md §3).");
    }

    @Test
    void everyCachedContextTogetherStaysUnderThePostgresConnectionCeiling() throws IOException {
        int maximumPoolSize =
                Integer.parseInt(testProperties().getProperty("spring.datasource.hikari.maximum-pool-size"));

        int worstCase = maximumPoolSize * CACHED_CONTEXT_BUDGET;
        int usable = POSTGRES_MAX_CONNECTIONS - SUPERUSER_RESERVED_CONNECTIONS;

        assertTrue(worstCase <= usable,
                "Onbellege alinan " + CACHED_CONTEXT_BUDGET + " test context'i havuz basina " + maximumPoolSize
                        + " baglanti ile " + worstCase + " baglanti ister; postgres:17 kabinde " + usable
                        + " tane var. Havuzu kucult ya da kabin max_connections degerini yukselt (CLAUDE.md §3).");
    }

    @Test
    void anIdleCachedContextDoesNotHoldItsWholePool() throws IOException {
        Properties properties = testProperties();

        assertEquals("0", properties.getProperty("spring.datasource.hikari.minimum-idle"),
                "minimum-idle sifir olmazsa Hikari havuzu bos context'lerde bile maximum-pool-size'a doldurur.");
        assertNotNull(properties.getProperty("spring.datasource.hikari.idle-timeout"),
                "idle-timeout verilmezse bos baglantilar kosu boyunca birakilmaz.");
    }
}
