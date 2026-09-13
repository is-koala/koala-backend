package com.exskylab.koala.support;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.Map;

public final class TestRabbit {

    private static final String USER = "koala-test";
    private static final String PASS = "test-only";

    private static final GenericContainer<?> BROKER = new GenericContainer<>("rabbitmq:4.1.3")
            .withExposedPorts(5672)
            .withEnv("RABBITMQ_DEFAULT_USER", USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", PASS)
            .withStartupTimeout(Duration.ofSeconds(120));

    private TestRabbit() {
    }

    public static synchronized void register(DynamicPropertyRegistry registry) {
        ensureStarted();
        registry.add("spring.rabbitmq.host", BROKER::getHost);
        registry.add("spring.rabbitmq.port", () -> BROKER.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> USER);
        registry.add("spring.rabbitmq.password", () -> PASS);
    }

    public static void declareDurableQueue(String queue) {
        ensureStarted();
        try (Connection connection = open(); var channel = connection.createChannel()) {
            channel.queueDeclare(queue, true, false, false, Map.of());
        } catch (Exception failed) {
            throw new IllegalStateException("Cannot prepare the existing " + queue + " queue", failed);
        }
    }

    private static synchronized void ensureStarted() {
        if (!BROKER.isRunning()) {
            BROKER.start();
        }
        try (Connection ignored = open()) {
        } catch (Exception failed) {
            throw new IllegalStateException("RabbitMQ did not accept AMQP connections", failed);
        }
    }

    private static Connection open() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(BROKER.getHost());
        factory.setPort(BROKER.getMappedPort(5672));
        factory.setUsername(USER);
        factory.setPassword(PASS);
        factory.setAutomaticRecoveryEnabled(false);
        factory.setConnectionTimeout(2_000);
        Exception last = null;
        for (int attempt = 0; attempt < 120; attempt++) {
            try {
                return factory.newConnection();
            } catch (Exception failed) {
                last = failed;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        }
        throw new IllegalStateException("RabbitMQ did not accept AMQP connections", last);
    }
}
