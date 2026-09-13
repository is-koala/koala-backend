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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffLogLanguageTest {

    private static final Pattern LOG_CALL =
            Pattern.compile("log\\.(?:trace|debug|info|warn|error)\\(([^;]*?)\\);", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    private static final Pattern TURKISH_LETTER = Pattern.compile("[çğıöşüÇĞİÖŞÜ]");

    @Test
    void staffSliceLogLinesAreEnglish() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : staffFiles()) {
            violations.addAll(turkishLogStringsIn(file.getFileName().toString(),
                    Files.readString(file, StandardCharsets.UTF_8)));
        }

        assertTrue(violations.isEmpty(),
                "Personel dilimindeki log satirlari Ingilizce olmali (§2aj):\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    void theScanSeesTheStaffLogSurface() throws IOException {
        int calls = 0;
        for (Path file : staffFiles()) {
            Matcher matcher = LOG_CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                calls++;
            }
        }
        assertTrue(calls >= 15, "Tarama personel log yuzeyini gormeli, aksi halde sessizce bosa kosar: " + calls);
    }

    @Test
    void theScanWouldCatchATurkishLogLine() {
        List<String> hits = turkishLogStringsIn("Example.java", """
                class Example {
                    void x() {
                        log.warn("Personel girişi reddedildi: {}");
                    }
                }
                """);
        assertEquals(List.of("Example.java -> Personel girişi reddedildi: {}"), hits);
    }

    @Test
    void theScanWouldCatchAnAsciiTurkishWord() {
        List<String> hits = turkishLogStringsIn("Example.java", """
                class Example {
                    void x() {
                        log.warn("reason was belirtilmedi");
                    }
                }
                """);
        assertEquals(List.of("Example.java -> reason was belirtilmedi"), hits);
    }

    private static List<String> turkishLogStringsIn(String fileName, String source) {
        List<String> hits = new ArrayList<>();
        Matcher call = LOG_CALL.matcher(source);
        while (call.find()) {
            Matcher literal = STRING_LITERAL.matcher(call.group(1));
            while (literal.find()) {
                String text = literal.group();
                String unquoted = text.substring(1, text.length() - 1);
                if (TURKISH_LETTER.matcher(unquoted).find() || unquoted.contains("belirtilmedi")) {
                    hits.add(fileName + " -> " + unquoted);
                }
            }
        }
        return hits;
    }

    private static List<Path> staffFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            return files
                    .filter(path -> path.getFileName().toString().startsWith("Staff"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }
}
