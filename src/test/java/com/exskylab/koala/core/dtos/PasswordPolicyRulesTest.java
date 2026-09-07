package com.exskylab.koala.core.dtos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyRulesTest {

    private static final Path DTOS = Path.of("src", "main", "java", "com", "exskylab", "koala", "core", "dtos");

    private static final Pattern FIELD =
            Pattern.compile("private\\s+\\S+\\s+(\\w*[Pp]assword)\\s*$");

    private static final Set<String> PASSWORDS_THE_USER_ALREADY_HAS = Set.of(
            "AuthLoginDto.java#password",
            "ChangePasswordDto.java#currentPassword");

    private record Field(Path file, String name, String declaration) {

        String id() {
            return file.getFileName() + "#" + name;
        }
    }

    @Test
    void everyFieldThatSetsAPasswordCarriesTheOneSharedRule() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Field field : passwordFields()) {
            if (PASSWORDS_THE_USER_ALREADY_HAS.contains(field.id())) {
                continue;
            }
            if (!field.declaration().contains("@ValidPassword")) {
                offenders.add(field.id());
            }
        }

        assertTrue(offenders.isEmpty(),
                "Parola alanı @ValidPassword taşımıyor, kural uçtan uca aynı olmaz: " + offenders);
    }

    @Test
    void noPasswordFieldRestatesTheRuleWithItsOwnAnnotations() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Field field : passwordFields()) {
            if (PASSWORDS_THE_USER_ALREADY_HAS.contains(field.id())) {
                continue;
            }
            if (field.declaration().contains("@Size") || field.declaration().contains("@Pattern")) {
                offenders.add(field.id());
            }
        }

        assertTrue(offenders.isEmpty(),
                "Parola alanı kendi uzunluk/karakter kuralını yazıyor, kural üç yerde üç türlü olur: " + offenders);
    }

    @Test
    void everyExemptionStillDescribesARealFieldThatChecksAnExistingPassword() throws IOException {
        Set<String> present = new LinkedHashSet<>();
        for (Field field : passwordFields()) {
            present.add(field.id());
        }

        List<String> stale = PASSWORDS_THE_USER_ALREADY_HAS.stream()
                .filter(exemption -> !present.contains(exemption))
                .sorted()
                .toList();

        assertTrue(stale.isEmpty(), "Muafiyet listesi bayatlamış, gerekmeyen satırlar düşürülmeli: " + stale);
    }

    @Test
    void theScanReachesEveryKnownPasswordField() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        for (Field field : passwordFields()) {
            found.add(field.id());
        }

        List<String> expected = List.of(
                "AuthCompleteRegistrationDto.java#password",
                "AuthLoginDto.java#password",
                "ResetPasswordDto.java#newPassword",
                "ChangePasswordDto.java#currentPassword",
                "ChangePasswordDto.java#newPassword",
                "ChangePasswordDto.java#confirmNewPassword",
                "CreateUserDto.java#password");

        List<String> missed = expected.stream().filter(id -> !found.contains(id)).toList();

        assertTrue(missed.isEmpty(), "Tarama bozulmuş, bilinen parola alanlarını göremiyor: " + missed);
    }

    private List<Field> passwordFields() throws IOException {
        List<Field> fields = new ArrayList<>();

        try (Stream<Path> files = Files.walk(DTOS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String declaration : Files.readString(file, StandardCharsets.UTF_8).split(";")) {
                    Matcher matcher = FIELD.matcher(declaration.strip());
                    if (matcher.find()) {
                        fields.add(new Field(file, matcher.group(1), declaration));
                    }
                }
            }
        }

        return fields;
    }
}
