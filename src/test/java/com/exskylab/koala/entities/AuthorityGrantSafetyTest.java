package com.exskylab.koala.entities;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorityGrantSafetyTest {

    private static final Pattern COLLECTION_REPLACEMENT = Pattern.compile("\\.setAuthorities\\(");

    private static final Set<String> ALLOWED_CALL_SITES = Set.of("AuthManager.java");

    @Test
    void nothingButRegistrationReplacesTheAuthoritiesCollection() throws IOException {
        List<String> callSites = new ArrayList<>();

        for (Path file : javaFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = COLLECTION_REPLACEMENT.matcher(source);

            while (matcher.find()) {
                int line = (int) source.substring(0, matcher.start()).chars().filter(c -> c == '\n').count() + 1;
                callSites.add(file.getFileName() + ":" + line);
            }
        }

        List<String> unexpected = callSites.stream()
                .filter(site -> !ALLOWED_CALL_SITES.contains(site.substring(0, site.indexOf(':'))))
                .toList();

        assertTrue(unexpected.isEmpty(),
                "setAuthorities koleksiyonu toptan degistirir; Hibernate tum satirlari silip yeniden yazdigi "
                        + "icin granted_at ve granted_by_id kaybolur. Kayitli bir kullaniciya rol verirken "
                        + "getAuthorities().add(...) kullanilmali (CLAUDE.md §2f2): " + unexpected);
    }

    @Test
    void theRegistrationCallSiteStillExists() throws IOException {
        List<String> callSites = new ArrayList<>();

        for (Path file : javaFiles()) {
            if (!ALLOWED_CALL_SITES.contains(file.getFileName().toString())) {
                continue;
            }
            if (COLLECTION_REPLACEMENT.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                callSites.add(file.getFileName().toString());
            }
        }

        assertEquals(List.of("AuthManager.java"), callSites,
                "Muafiyet listesi artik gecerli degil, guncellenmeli.");
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
