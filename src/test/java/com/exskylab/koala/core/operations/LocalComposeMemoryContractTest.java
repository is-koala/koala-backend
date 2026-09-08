package com.exskylab.koala.core.operations;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalComposeMemoryContractTest {

    private static final Path COMPOSE = Path.of("docker-compose.yml");
    private static final Path ENV_EXAMPLE = Path.of(".env.example");

    @Test
    void capsEveryLongRunningLocalServiceAndSetsRabbitMqWatermark() throws IOException {
        String compose = Files.readString(COMPOSE, StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(compose.contains("mem_limit: ${KOALA_APP_MEM:-1200m}")),
                () -> assertTrue(compose.contains("mem_limit: ${KOALA_DB_MEM:-768m}")),
                () -> assertTrue(compose.contains("mem_limit: ${KOALA_RABBITMQ_MEM:-384m}")),
                () -> assertTrue(compose.contains(
                        "RABBITMQ_VM_MEMORY_HIGH_WATERMARK: ${RABBITMQ_MEM_WATERMARK:-0.6}")));
    }

    @Test
    void publishesEveryLocalMemoryOverrideInTheExampleEnvironment() throws IOException {
        String environment = Files.readString(ENV_EXAMPLE, StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(environment.contains("KOALA_APP_MEM=1200m")),
                () -> assertTrue(environment.contains("KOALA_DB_MEM=768m")),
                () -> assertTrue(environment.contains("KOALA_RABBITMQ_MEM=384m")),
                () -> assertTrue(environment.contains("RABBITMQ_MEM_WATERMARK=0.6")));
    }
}
