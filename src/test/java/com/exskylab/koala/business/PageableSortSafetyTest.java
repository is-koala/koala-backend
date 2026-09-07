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
                    "saf devretme; sayfalama CompanyFollowManager icinde dogrulaniyor"),
            new Exemption("JobManager", "search",
                    "saf devretme; sayfalama ortak listJobs yardimcisinda dogrulaniyor"),
            new Exemption("JobManager", "getJobsOfCurrentUser",
                    "saf devretme; sayfalama ortak listJobs yardimcisinda dogrulaniyor"),
            new Exemption("JobManager", "getJobsOfCompany",
                    "saf devretme; sayfalama ortak listJobs yardimcisinda dogrulaniyor"));

    @Test
    void everyPagedQuerySanitisesTheRequestedSort() throws IOException {
        List<String> unguarded = new ArrayList<>();

        for (PagedMethod method : pagedMethods()) {
            boolean exempt = EXEMPTIONS.stream().anyMatch(e -> e.manager().equals(method.manager())
                    && method.signature().contains(" " + e.method() + "("));

            if (!exempt && !method.body().contains("rejectUnknownSort")) {
                unguarded.add(method.manager() + " -> " + method.signature().replaceAll("\\s+", " ").trim());
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

    @Test
    void everyExemptionStillDescribesARealPagedMethodThatDelegates() throws IOException {
        List<PagedMethod> methods = pagedMethods();
        List<String> stale = new ArrayList<>();

        for (Exemption exemption : EXEMPTIONS) {
            boolean stillNeeded = methods.stream()
                    .anyMatch(m -> m.manager().equals(exemption.manager())
                            && m.signature().contains(" " + exemption.method() + "(")
                            && !m.body().contains("rejectUnknownSort"));

            if (!stillNeeded) {
                stale.add(exemption.manager() + "#" + exemption.method());
            }
        }

        assertTrue(stale.isEmpty(),
                ("Muafiyet bayatladi: adlandirilan metot ya yok, ya artik sayfali degil, "
                        + "ya da sanitizasyonu kendisi yapiyor. Listeden dusurulmeli:\n")
                        + String.join("\n", stale));
    }

    private record PagedMethod(String manager, String signature, String body) {
    }

    private static List<PagedMethod> pagedMethods() throws IOException {
        List<PagedMethod> methods = new ArrayList<>();

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

                    methods.add(new PagedMethod(manager, signature, source.substring(method.end(),
                            Math.min(source.length(), method.end() + BODY_WINDOW))));
                }
            }
        }

        return methods;
    }
}
