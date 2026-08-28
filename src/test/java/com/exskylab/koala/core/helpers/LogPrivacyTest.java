package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogPrivacyTest {

    private static final Pattern LOG_CALL =
            Pattern.compile("log\\.(?:trace|debug|info|warn|error)\\(([^;]*?)\\);", Pattern.DOTALL);

    private static final List<String> FORBIDDEN_ARGUMENTS = List.of(
            "getIban()", "getTcIdentityNumber()", "getPassword()", "getRefreshTokenHash()",
            "getTcDocumentNumber()", "getSecretKey()", "getApiKey()", "getApiHash()");

    @Test
    void noLogStatementPassesRawSensitiveDataAsAnArgument() throws IOException {
        List<String> violations = scan(FORBIDDEN_ARGUMENTS);

        assertTrue(violations.isEmpty(),
                "Bu log cagrilari maskelenmemis hassas veri tasiyor:\n  " + String.join("\n  ", violations));
    }

    @Test
    void emailAndPhoneReachLogsOnlyThroughLogMasks() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = LOG_CALL.matcher(source);
            while (matcher.find()) {
                String call = matcher.group(1);
                for (String accessor : List.of("getEmail()", "getPhoneNumber()", "getDestinationEmail()",
                        "getDestinationPhoneNumber()", "getNewEmail()", "getEmailAttempted()")) {
                    if (call.contains(accessor) && !call.contains("LogMasks.")) {
                        violations.add(file.getFileName() + ": " + oneLine(call));
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Bu log cagrilari e-posta veya telefonu maskelemeden yaziyor:\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    void theScannerActuallySeesTheLogStatements() throws IOException {
        int found = 0;
        for (Path file : javaFiles()) {
            Matcher matcher = LOG_CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                found++;
            }
        }
        assertTrue(found > 150, "Beklenenden az log cagrisi tarandi: " + found);
    }

    private List<String> scan(List<String> forbidden) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = LOG_CALL.matcher(source);
            while (matcher.find()) {
                String call = matcher.group(1);
                for (String accessor : forbidden) {
                    if (call.contains(accessor)) {
                        violations.add(file.getFileName() + ": " + oneLine(call));
                    }
                }
            }
        }
        return violations;
    }

    private List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private String oneLine(String call) {
        String flat = call.replaceAll("\\s+", " ").trim();
        return flat.length() > 120 ? flat.substring(0, 120) + "..." : flat;
    }

    private static final List<String> PII_VARIABLE_NAMES = List.of(
            "email", "newEmail", "emailAttempted", "recipientEmail",
            "phoneNumber", "newPhoneNumber", "oldPhoneNumber", "destinationPhoneNumber",
            "iban", "tcIdentityNumber", "password", "rawPassword", "to");

    @Test
    void noLogStatementPassesRawPiiHeldInALocalVariableOrParameter() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = LOG_CALL.matcher(source);

            while (matcher.find()) {
                String call = matcher.group(1);
                if (call.contains("LogMasks.")) {
                    continue;
                }

                String arguments = call.contains("\",") ? call.substring(call.lastIndexOf('"') + 1) : "";

                for (String name : PII_VARIABLE_NAMES) {
                    if (arguments.matches("(?s).*\\b" + Pattern.quote(name) + "\\b.*")) {
                        violations.add(file.getFileName() + " -> '" + name + "': " + oneLine(call));
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Bu log cagrilari degisken uzerinden maskelenmemis kisisel veri tasiyor:\n  "
                        + String.join("\n  ", violations));
    }
}
