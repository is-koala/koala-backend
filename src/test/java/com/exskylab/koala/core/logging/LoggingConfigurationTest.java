package com.exskylab.koala.core.logging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {

    private static final Path LOGBACK = Path.of("src", "main", "resources", "logback-spring.xml");
    private static final Path APPLICATION = Path.of("src", "main", "resources", "application.yaml");

    private static final Pattern APPENDER = Pattern.compile("<appender\\s+name=\"([^\"]+)\"");
    private static final Pattern PROFILE_BLOCK =
            Pattern.compile("<springProfile[^>]*>(.*?)</springProfile>", Pattern.DOTALL);
    private static final Pattern CONSOLE_PATTERN = Pattern.compile("<pattern>(.*?)</pattern>", Pattern.DOTALL);

    @Test
    void everyAppenderIsDeclaredAndReferencedInsideTheSameProfileBlock() throws IOException {
        String config = Files.readString(LOGBACK, StandardCharsets.UTF_8);
        List<String> violations = new ArrayList<>();

        Matcher appenders = APPENDER.matcher(config);
        while (appenders.find()) {
            String name = appenders.group(1);
            boolean referencedWhereDeclared = false;

            Matcher profiles = PROFILE_BLOCK.matcher(config);
            while (profiles.find()) {
                String block = profiles.group(1);
                if (block.contains("<appender name=\"" + name + "\"")
                        && block.contains("<appender-ref ref=\"" + name + "\"")) {
                    referencedWhereDeclared = true;
                }
            }

            if (!referencedWhereDeclared) {
                violations.add(name);
            }
        }

        assertTrue(violations.isEmpty(),
                "Bu appender'lar profil bloklarinin disinda kaliyor, Logback her acilista "
                        + "'not referenced' uyarisi veriyor: " + violations);
    }

    @Test
    void theConsolePatternCarriesTheDateAndTheZoneOffset() throws IOException {
        String config = Files.readString(LOGBACK, StandardCharsets.UTF_8);
        Matcher pattern = CONSOLE_PATTERN.matcher(config);

        assertTrue(pattern.find(), "Konsol deseni bulunamadi");
        String consolePattern = pattern.group(1);

        assertTrue(consolePattern.contains("yyyy") || consolePattern.contains("LOG_DATEFORMAT_PATTERN"),
                "Konsol deseni tarih tasimiyor: " + consolePattern);
        assertTrue(consolePattern.contains("XXX") || consolePattern.contains("LOG_DATEFORMAT_PATTERN"),
                "Konsol deseni saat dilimi tasimiyor: " + consolePattern);
    }

    @Test
    void theConsolePatternPrintsTheRequestId() throws IOException {
        String config = Files.readString(LOGBACK, StandardCharsets.UTF_8);

        assertTrue(config.contains("%X{requestId"), "Konsol deseni requestId basmiyor");
    }

    @Test
    void theAccessLoggerLevelKnobPointsAtALoggerThatExists() throws IOException {
        String application = Files.readString(APPLICATION, StandardCharsets.UTF_8);
        assertTrue(application.contains("http.access: ${ACCESS_LOG_LEVEL"),
                "ACCESS_LOG_LEVEL ayari kayboldu");

        String filter = Files.readString(
                Path.of("src", "main", "java", "com", "exskylab", "koala", "core", "configs",
                        "RequestLoggingFilter.java"), StandardCharsets.UTF_8);
        assertTrue(filter.contains("\"http.access\""),
                "RequestLoggingFilter 'http.access' adinda bir logger kullanmiyor, ACCESS_LOG_LEVEL olu config");
    }

    @Test
    void theUserAgentAnalyserIsQuietedAtStartup() throws IOException {
        String application = Files.readString(APPLICATION, StandardCharsets.UTF_8);
        assertTrue(application.contains("nl.basjes:"),
                "Yauaa (nl.basjes) acilista yaklasik 22 saniye logluyor, seviyesi ayarlanmamis");
    }

    @Test
    void theLoggersEndpointIsExposedSoLevelsChangeWithoutARedeploy() throws IOException {
        String application = Files.readString(APPLICATION, StandardCharsets.UTF_8);
        Matcher include = Pattern.compile("include:\\s*(.+)").matcher(application);

        assertTrue(include.find(), "Actuator exposure ayari bulunamadi");
        assertTrue(include.group(1).contains("loggers"),
                "/actuator/loggers acik degil: " + include.group(1));
    }
}
