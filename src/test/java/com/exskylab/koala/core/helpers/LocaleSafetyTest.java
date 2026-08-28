package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LocaleSafetyTest {

    private static final Pattern BARE_CASE_CHANGE =
            Pattern.compile("\\.to(?:Upper|Lower)Case\\(\\s*\\)");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void noCaseConversionRelisOnTheDefaultLocale() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = BARE_CASE_CHANGE.matcher(source);

            while (matcher.find()) {
                int line = (int) source.substring(0, matcher.start()).chars().filter(c -> c == '\n').count() + 1;
                violations.add(file.getFileName() + ":" + line);
            }
        }

        assertTrue(violations.isEmpty(),
                "Türkçe locale'de 'i' harfi 'İ' olur; makineye bakan metinde Locale.ROOT, "
                        + "insana bakan metinde açık Türkçe locale verilmeli: " + violations);
    }

    @Test
    void theTurkishLocaleWouldHaveBrokenTheseIfLeftToTheDefault() {
        Locale turkish = Locale.of("tr", "TR");

        assertEquals("VALIDATION", "validation".toUpperCase(Locale.ROOT));
        assertNotEquals("VALIDATION", "validation".toUpperCase(turkish),
                "Türkçe locale gerçekten farklı sonuç üretiyor, bu test anlamlı");

        assertEquals("INDIVIDUAL", "individual".toUpperCase(Locale.ROOT),
                "enum adi Locale.ROOT ile eslesmeli");
    }

    @Test
    void turkishNamesStillCapitaliseTheTurkishWay() {
        Locale turkish = Locale.of("tr", "TR");

        assertEquals("İ", "i".toUpperCase(turkish));
        assertEquals("I", "i".toUpperCase(Locale.ROOT));
    }
}
