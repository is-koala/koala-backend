package com.exskylab.koala.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EcsStructuredLoggingTest {

    @Test
    void aFlatMdcKeyStaysFlatAndADottedOneIsNestedByTheEcsFormatter() {
        String json = encode(Map.of(
                LogContext.REQUEST_ID, "6f1b2d5c-0000-4000-8000-000000000001",
                LogContext.USER_ID, "9a3c7e10-0000-4000-8000-000000000002",
                "trace.id", "4bf92f3577b34da6a3ce929d0e0e4736"));

        assertTrue(json.contains("\"requestId\":\"6f1b2d5c-0000-4000-8000-000000000001\""),
                "requestId duz bir alan olarak cikmali: " + json);
        assertTrue(json.contains("\"userId\":\"9a3c7e10-0000-4000-8000-000000000002\""),
                "userId duz bir alan olarak cikmali: " + json);
        assertTrue(json.contains("\"trace\":{\"id\":\"4bf92f3577b34da6a3ce929d0e0e4736\"}"),
                "Noktali MDC anahtari ECS'te ic ice JSON'a donusmeli: " + json);
    }

    private String encode(Map<String, String> context) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Environment environment = new MockEnvironment().withProperty("logging.structured.format.console", "ecs");
        Object previous = loggerContext.getObject(Environment.class.getName());
        loggerContext.putObject(Environment.class.getName(), environment);

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(loggerContext);
        encoder.setFormat("ecs");
        encoder.start();

        try {
            LoggingEvent event = new LoggingEvent();
            event.setLoggerName("http.access");
            event.setLevel(Level.INFO);
            event.setMessage("GET /users/me");
            event.setTimeStamp(System.currentTimeMillis());
            event.setMDCPropertyMap(context);
            return new String(encoder.encode(event), StandardCharsets.UTF_8);
        } finally {
            encoder.stop();
            if (previous == null) {
                loggerContext.removeObject(Environment.class.getName());
            } else {
                loggerContext.putObject(Environment.class.getName(), previous);
            }
        }
    }
}
