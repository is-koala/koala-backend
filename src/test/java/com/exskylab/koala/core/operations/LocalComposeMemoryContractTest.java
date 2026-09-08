package com.exskylab.koala.core.operations;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class LocalComposeMemoryContractTest {

    private static final Path COMPOSE = Path.of("docker-compose.yml");
    private static final Path ENV_EXAMPLE = Path.of(".env.example");
    private static final Pattern DEFAULTABLE_VARIABLE = Pattern.compile("^\\$\\{([A-Z0-9_]+):-([^}]+)}$");

    @Test
    void resolvesTheDefaultMemoryBudgetOnTheCorrectServices() throws IOException {
        Map<String, Object> services = services();

        assertAll(
                () -> assertEquals("1200m", resolve(value(services, "koala-app", "mem_limit"), Map.of())),
                () -> assertEquals("768m", resolve(value(services, "koala-db", "mem_limit"), Map.of())),
                () -> assertEquals("384m", resolve(value(services, "koala-rabbitmq", "mem_limit"), Map.of())),
                () -> assertEquals("0.6", resolve(rabbitMqWatermark(services), Map.of())));
    }

    @Test
    void resolvesCustomMemoryOverridesOnTheCorrectServices() throws IOException {
        Map<String, Object> services = services();
        Map<String, String> overrides = Map.of(
                "KOALA_APP_MEM", "1300m",
                "KOALA_DB_MEM", "800m",
                "KOALA_RABBITMQ_MEM", "400m",
                "RABBITMQ_MEM_WATERMARK", "0.55");

        assertAll(
                () -> assertEquals("1300m", resolve(value(services, "koala-app", "mem_limit"), overrides)),
                () -> assertEquals("800m", resolve(value(services, "koala-db", "mem_limit"), overrides)),
                () -> assertEquals("400m", resolve(value(services, "koala-rabbitmq", "mem_limit"), overrides)),
                () -> assertEquals("0.55", resolve(rabbitMqWatermark(services), overrides)));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> services() throws IOException {
        try (InputStream input = Files.newInputStream(COMPOSE)) {
            Object document = new Yaml().load(input);
            Map<String, Object> root = assertInstanceOf(Map.class, document);
            return assertInstanceOf(Map.class, root.get("services"));
        }
    }

    private static String value(Map<String, Object> services, String serviceName, String key) {
        Map<?, ?> service = assertInstanceOf(Map.class, services.get(serviceName));
        return assertInstanceOf(String.class, service.get(key));
    }

    private static String rabbitMqWatermark(Map<String, Object> services) {
        Map<?, ?> rabbitMq = assertInstanceOf(Map.class, services.get("koala-rabbitmq"));
        Map<?, ?> environment = assertInstanceOf(Map.class, rabbitMq.get("environment"));
        return assertInstanceOf(String.class, environment.get("RABBITMQ_VM_MEMORY_HIGH_WATERMARK"));
    }

    private static String resolve(String expression, Map<String, String> overrides) {
        Matcher matcher = DEFAULTABLE_VARIABLE.matcher(expression);
        assertTrue(matcher.matches(), "A defaultable Compose variable was expected: " + expression);
        return overrides.getOrDefault(matcher.group(1), matcher.group(2));
    }
}
