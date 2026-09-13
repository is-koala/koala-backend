package com.exskylab.koala.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRabbitTest {

    private static final Path TEST_JAVA = Path.of("src", "test", "java");
    private static final Path POM = Path.of("pom.xml");
    private static final Path TEST_RABBIT =
            Path.of("src/test/java/com/exskylab/koala/support/TestRabbit.java");

    private static final List<String> BROKER_ITS = List.of(
            "EmailRetryIT.java",
            "RabbitLogContextIT.java",
            "NotificationOutboxRabbitIT.java",
            "UserProfileSyncOutboxRabbitIT.java");

    @Test
    void brokerItsShareOneNonManagementImage() throws IOException {
        String helper = Files.readString(TEST_RABBIT, StandardCharsets.UTF_8);
        assertTrue(helper.contains("rabbitmq:4.1.3\""), "TestRabbit must pin rabbitmq:4.1.3");
        assertFalse(helper.contains("-management"), "management UI is CPU, not a test dependency");
        assertTrue(helper.contains("newConnection()"), "port-open is not AMQP-ready");
    }

    @Test
    void noOtherTestStartsItsOwnBroker() throws IOException {
        try (Stream<Path> files = Files.walk(TEST_JAVA)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("TestRabbit.java")))
                    .filter(path -> !path.endsWith(Path.of("TestRabbitTest.java")))
                    .filter(TestRabbitTest::startsOwnBroker)
                    .toList();
            assertTrue(offenders.isEmpty(), "own GenericContainer broker: " + offenders);
        }
    }

    @Test
    void theTwoForkRunExcludesBrokerTests() throws IOException {
        String pom = Files.readString(POM, StandardCharsets.UTF_8);
        String defaultRun = executionBefore(pom, "<id>identity-multipart-budget</id>");
        for (String brokerIt : BROKER_ITS) {
            assertTrue(
                    defaultRun.contains("<exclude>**/" + brokerIt + "</exclude>"),
                    brokerIt + " still runs in the two-fork suite");
        }
    }

    @Test
    void theBrokerExecutionIsASingleFork() throws IOException {
        String pom = Files.readString(POM, StandardCharsets.UTF_8);
        assertTrue(pom.contains("<id>rabbit-broker</id>"), "missing rabbit-broker failsafe execution");
        String brokerRun = executionAfter(pom, "<id>rabbit-broker</id>");
        assertTrue(brokerRun.contains("<forkCount>1</forkCount>"), "rabbit ITs must not share the two-fork CPU");
        for (String brokerIt : BROKER_ITS) {
            assertTrue(
                    brokerRun.contains("<include>**/" + brokerIt + "</include>"),
                    brokerIt + " missing from rabbit-broker includes");
        }
    }

    private static boolean startsOwnBroker(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            return text.contains("new GenericContainer<>(\"rabbitmq")
                    || text.contains("new GenericContainer<String>(\"rabbitmq");
        } catch (IOException failed) {
            throw new IllegalStateException(path.toString(), failed);
        }
    }

    private static String executionBefore(String pom, String marker) {
        int at = pom.indexOf(marker);
        assertTrue(at > 0, "missing " + marker);
        return pom.substring(0, at);
    }

    private static String executionAfter(String pom, String marker) {
        int at = pom.indexOf(marker);
        assertTrue(at >= 0, "missing " + marker);
        int next = pom.indexOf("<execution>", at + marker.length());
        return next < 0 ? pom.substring(at) : pom.substring(at, next);
    }
}
