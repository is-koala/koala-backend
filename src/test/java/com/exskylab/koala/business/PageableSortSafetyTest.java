package com.exskylab.koala.business;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PageableSortSafetyTest {

    private static final Path MANAGERS = Paths.get("src/main/java/com/exskylab/koala/business/concretes");

    private static final Pattern PAGED_METHOD =
            Pattern.compile("public [^\\n(]*\\(([^)]*\\bPageable\\b[^)]*)\\)\\s*\\{");

    private static final int BODY_WINDOW = 900;

    private record Exemption(String manager, String method, String reason) {
    }

    private static final List<Exemption> EXEMPTIONS = List.of(
            new Exemption("CompanyManager", "getFollowedCompanies",
                    "saf devretme; sayfalama CompanyFollowManager icinde dogrulaniyor"));

    @Test
    void everyPagedQuerySanitisesTheRequestedSort() throws IOException {
        List<String> unguarded = new ArrayList<>();

        try (Stream<Path> files = Files.list(MANAGERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file);
                String manager = file.getFileName().toString().replace(".java", "");

                Matcher method = PAGED_METHOD.matcher(source);
                while (method.find()) {
                    String signature = method.group();
                    if (signature.contains("public " + manager + "(")) {
                        continue;
                    }

                    String body = source.substring(method.end(),
                            Math.min(source.length(), method.end() + BODY_WINDOW));

                    boolean exempt = EXEMPTIONS.stream().anyMatch(e -> e.manager().equals(manager)
                            && signature.contains(" " + e.method() + "("));

                    if (!exempt && !body.contains("rejectUnknownSort")) {
                        unguarded.add(manager + " -> " + signature.replaceAll("\\s+", " ").trim());
                    }
                }
            }
        }

        List<String> silent = EXEMPTIONS.stream()
                .filter(e -> e.reason() == null || e.reason().isBlank())
                .map(Exemption::manager)
                .toList();
        assertTrue(silent.isEmpty(), "gerekcesiz muafiyet: " + silent);

        assertTrue(unguarded.isEmpty(),
                ("Sayfali sorgu istemciden gelen sort alanini dogrulamadan DAO'ya geciriyor. "
                        + "Bilinmeyen alan Spring Data'da InvalidDataAccessApiUsageException firlatir ve 500 doner; "
                        + "pageableSanitizer.rejectUnknownSort ile 400'e cevrilmeli:\n")
                        + String.join("\n", unguarded));
    }
}
