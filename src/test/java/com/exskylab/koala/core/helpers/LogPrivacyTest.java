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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogPrivacyTest {

    private static final Pattern LOG_CALL =
            Pattern.compile("log\\.(?:trace|debug|info|warn|error)\\(([^;]*?)\\);", Pattern.DOTALL);

    private static final List<String> FORBIDDEN_ARGUMENTS = List.of(
            "getIban()", "getTcIdentityNumber()", "getPassword()", "getRefreshTokenHash()",
            "getTcDocumentNumber()", "getSecretKey()", "getApiKey()", "getApiHash()",
            "getName()", "getFirstName()", "getLastName()", "getFullName()", "getMiddleName()",
            "getCompanyName()", "getContactName()", "getSenderName()", "getGrantedByName()",
            "getDisplayName()",
            "getBirthdate()", "getBirthDate()", "getDateOfBirth()", "getGender()",
            "getAbout()", "getQualifications()", "getInterests()",
            "getVerificationCode()", "getOtp()", "getOtpCode()", "getSmsCode()",
            "getAddress()", "getNeighborhood()", "getThoroughfare()", "getSiteName()",
            "getBlock()", "getBuildingName()", "getBuildingNumber()", "getFloor()",
            "getUnitNumber()", "getPostalCode()", "getAdditionalDirections()",
            "getLatitude()", "getLongitude()");

    private static final Pattern FORBIDDEN_TOKEN_ACCESSOR =
            Pattern.compile("get[A-Za-z]*[Tt]oken[A-Za-z]*\\(\\)");

    private static final List<String> MASK_REQUIRED_ACCESSORS = List.of(
            "getEmail()", "getPhoneNumber()", "getDestinationEmail()",
            "getDestinationPhoneNumber()", "getNewEmail()", "getEmailAttempted()");

    private static final List<String> PII_VARIABLE_NAMES = List.of(
            "email", "newEmail", "emailAttempted", "recipientEmail",
            "phoneNumber", "newPhoneNumber", "oldPhoneNumber", "destinationPhoneNumber",
            "iban", "tcIdentityNumber", "password", "rawPassword", "to",
            "firstName", "lastName", "fullName", "birthDate", "birthdate", "dateOfBirth",
            "verificationCode", "otpCode", "smsCode", "resetCode", "address");

    private static final String PII_NAME_SUFFIX = "\\b(?!\\s*(?:\\(|\\.get))";

    private static final Pattern PII_VARIABLE_TOKEN =
            Pattern.compile("(?<![.\\w])[A-Za-z]*[Tt]oken" + PII_NAME_SUFFIX);

    @Test
    void noLogStatementPassesRawSensitiveDataAsAnArgument() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = LOG_CALL.matcher(source);
            while (matcher.find()) {
                String call = matcher.group(1);
                for (String accessor : FORBIDDEN_ARGUMENTS) {
                    if (call.contains(accessor)) {
                        violations.add(file.getFileName() + " -> '" + accessor + "': " + oneLine(call));
                    }
                }
                Matcher tokenAccessor = FORBIDDEN_TOKEN_ACCESSOR.matcher(call);
                while (tokenAccessor.find()) {
                    violations.add(file.getFileName() + " -> '" + tokenAccessor.group() + "': " + oneLine(call));
                }
            }
        }

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
                for (String argument : argumentsOf(call)) {
                    if (argument.contains("LogMasks.")) {
                        continue;
                    }
                    for (String accessor : MASK_REQUIRED_ACCESSORS) {
                        if (argument.contains(accessor)) {
                            violations.add(file.getFileName() + " -> '" + accessor + "': " + oneLine(call));
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Bu log cagrilari e-posta veya telefonu maskelemeden yaziyor:\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    void noLogStatementPassesRawPiiHeldInALocalVariableOrParameter() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = LOG_CALL.matcher(source);

            while (matcher.find()) {
                String call = matcher.group(1);
                for (String argument : argumentsOf(call)) {
                    if (argument.contains("LogMasks.")) {
                        continue;
                    }
                    for (String name : PII_VARIABLE_NAMES) {
                        if (Pattern.compile("(?<![.\\w])" + Pattern.quote(name) + PII_NAME_SUFFIX)
                                .matcher(argument).find()) {
                            violations.add(file.getFileName() + " -> '" + name + "': " + oneLine(call));
                        }
                    }
                    Matcher token = PII_VARIABLE_TOKEN.matcher(argument);
                    while (token.find()) {
                        violations.add(file.getFileName() + " -> '" + token.group() + "': " + oneLine(call));
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Bu log cagrilari degisken uzerinden maskelenmemis kisisel veri tasiyor:\n  "
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

    @Test
    void theScannerActuallySeesTheArgumentsOfThoseLogStatements() throws IOException {
        int withArguments = 0;
        for (Path file : javaFiles()) {
            Matcher matcher = LOG_CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                if (!argumentsOf(matcher.group(1)).isEmpty()) {
                    withArguments++;
                }
            }
        }
        assertTrue(withArguments > 100, "Beklenenden az log argumani cozumlendi: " + withArguments);
    }

    @Test
    void argumentsAreStillSeenWhenALaterArgumentCarriesItsOwnStringLiteral() {
        String call = "\"Updating first name to: {}, from {}\", firstName, verified ? \"yes\" : \"no\"";

        List<String> arguments = argumentsOf(call);

        assertTrue(arguments.contains("firstName"),
                "Kendi metin sabitini tasiyan argumandan onceki argumanlar kayboldu: " + arguments);
    }

    @Test
    void commasInsideTheMessageDoNotSplitTheArgumentList() {
        String call = "\"a, b, c {}\", token";

        assertTrue(argumentsOf(call).contains("token"), "Mesajdaki virguller argumanlara karisti.");
        assertFalse(argumentsOf(call).contains("b"), "Mesaj metni arguman sanildi.");
    }

    private List<String> argumentsOf(String call) {
        String code = withoutStringLiterals(call);
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                addIfPresent(arguments, current);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addIfPresent(arguments, current);
        return arguments;
    }

    private void addIfPresent(List<String> arguments, StringBuilder current) {
        String argument = current.toString().trim();
        if (!argument.isEmpty()) {
            arguments.add(argument);
        }
    }

    private String withoutStringLiterals(String call) {
        StringBuilder code = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < call.length(); i++) {
            char c = call.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            code.append(c);
        }
        return code.toString();
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
}
