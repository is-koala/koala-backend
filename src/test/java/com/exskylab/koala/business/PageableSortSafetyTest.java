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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageableSortSafetyTest {

    private static final Path MANAGERS = Paths.get("src/main/java/com/exskylab/koala/business/concretes");

    /**
     * `throws` yan tumcesi eski `)\\s*\\{` deseninin disinda kaliyordu.
     * Govde artik sabit 900 karakterlik pencere degil, eslesen suslu parantez.
     */
    private static final Pattern PAGED_METHOD =
            Pattern.compile("public [^\\n(]*\\(([^)]*\\bPageable\\b[^)]*)\\)(?:\\s*throws[^{]+)?\\s*\\{");

    private record Exemption(String manager, String method, String reason) {
    }

    private static final List<Exemption> EXEMPTIONS = List.of(
            new Exemption("CompanyManager", "getFollowedCompanies",
                    "saf devretme; sayfalama CompanyFollowManager icinde dogrulaniyor"),
            new Exemption("JobManager", "search",
                    "saf devretme; sayfalama ortak listJobs yardimcisinda dogrulaniyor, "
                            + "allowlist yalniz lat/lng verildiginde distance alanini icerir (#270)"),
            new Exemption("JobManager", "getJobsOfCurrentUser",
                    "saf devretme; sayfalama ortak listJobs yardimcisinda dogrulaniyor"),
            new Exemption("JobManager", "getJobsOfCompany",
                    "saf devretme; sayfalama ortak listJobs yardimcisinda dogrulaniyor"),
            new Exemption("PayoutManager", "listForStaff",
                    "istemci sort'u yok sayiliyor; kuyruk sabit createdAt DESC kullanir"),
            new Exemption("StaffPayoutManager", "listForStaff",
                    "saf devretme; izin kapisindan sonra PayoutManager.listForStaff istemci sort'unu yok sayar"),
            new Exemption("AdminDetailManager", "searchCompanies",
                    "saf devretme; sayfalama CompanyManager.searchCompanies icinde dogrulaniyor (#283)"),
            new Exemption("AdminDetailManager", "searchPayments",
                    "saf devretme; sayfalama PaymentManager.searchForStaff icinde dogrulaniyor (#301)"));

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

    @Test
    void aPagedMethodWithThrowsIsStillFound() {
        String source = """
                public Page<Foo> search(Pageable pageable) throws DataAccessException {
                    return dao.findAll(pageable);
                }
                """;

        List<PagedMethod> found = pagedMethodsIn("ExampleManager", source);
        assertEquals(1, found.size(), "throws yan tumcesi sayfali metodu gizledi: " + found);
        assertTrue(found.getFirst().body().contains("dao.findAll"),
                "throws'lu metodun govdesi okunamadi: " + found.getFirst().body());
    }

    @Test
    void aFollowingSanitisedMethodDoesNotGuardThePreviousOne() {
        String source = """
                public Page<Foo> first(Pageable pageable) {
                    return dao.findAll(pageable);
                }
                public Page<Bar> second(Pageable pageable) {
                    return pageableSanitizer.rejectUnknownSort(pageable, Set.of("id"));
                }
                """;

        List<PagedMethod> found = pagedMethodsIn("ExampleManager", source);
        assertEquals(2, found.size());
        assertFalse(found.getFirst().body().contains("rejectUnknownSort"),
                "sonraki metodun sanitizasyonu onceki govdeye tasarak koruma gibi gorundu");
        assertTrue(found.get(1).body().contains("rejectUnknownSort"));
    }

    private record PagedMethod(String manager, String signature, String body) {
    }

    private static List<PagedMethod> pagedMethods() throws IOException {
        List<PagedMethod> methods = new ArrayList<>();

        try (Stream<Path> files = Files.list(MANAGERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file);
                String manager = file.getFileName().toString().replace(".java", "");
                methods.addAll(pagedMethodsIn(manager, source));
            }
        }

        return methods;
    }

    static List<PagedMethod> pagedMethodsIn(String manager, String source) {
        List<PagedMethod> methods = new ArrayList<>();
        Matcher method = PAGED_METHOD.matcher(source);
        while (method.find()) {
            String signature = method.group();
            if (signature.contains("public " + manager + "(")) {
                continue;
            }
            int openBrace = method.end() - 1;
            methods.add(new PagedMethod(manager, signature, methodBody(source, openBrace)));
        }
        return methods;
    }

    static String methodBody(String source, int openBraceIndex) {
        int depth = 0;
        for (int i = openBraceIndex; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBraceIndex + 1, i);
                }
            }
        }
        return source.substring(openBraceIndex + 1);
    }
}
