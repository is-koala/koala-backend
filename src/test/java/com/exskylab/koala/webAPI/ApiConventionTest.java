package com.exskylab.koala.webAPI;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ApiConventionTest {

    private static final Path CONTROLLERS = Paths.get("src/main/java/com/exskylab/koala/webAPI");
    private static final Path DTOS = Paths.get("src/main/java/com/exskylab/koala/core/dtos");
    private static final Path SECURITY_CONFIG =
            Paths.get("src/main/java/com/exskylab/koala/core/configs/SecurityConfig.java");

    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]*)\"\\)");
    private static final Pattern METHOD_MAPPING =
            Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping(?:\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\")?");
    private static final Pattern ADMIN_ONLY_RULE =
            Pattern.compile("\\.requestMatchers\\(([^)]*)\\)\\s*\\.has(?:Any)?Role\\(\\s*\"ADMIN\"\\s*\\)");
    private static final Pattern QUOTED_PATH = Pattern.compile("\"([^\"]*)\"");

    private record Route(String verb, String path, String controller) {
    }

    private static List<Route> routes() throws IOException {
        List<Route> routes = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher base = CLASS_MAPPING.matcher(source);
                String prefix = base.find() ? base.group(1) : "";

                Matcher method = METHOD_MAPPING.matcher(source);
                while (method.find()) {
                    String suffix = method.group(2) == null ? "" : method.group(2);
                    routes.add(new Route(method.group(1).toUpperCase(Locale.ROOT), prefix + suffix,
                            file.getFileName().toString()));
                }
            }
        }
        return routes;
    }

    private static List<Path> dtoFiles() throws IOException {
        try (Stream<Path> files = Files.walk(DTOS)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    private static List<String> adminOnlyPaths(String config) {
        List<String> paths = new ArrayList<>();
        Matcher rule = ADMIN_ONLY_RULE.matcher(config);
        while (rule.find()) {
            Matcher path = QUOTED_PATH.matcher(rule.group(1));
            while (path.find()) {
                paths.add(path.group(1));
            }
        }
        return paths;
    }

    @Test
    void everyRouteStartsAtTheRootAndCarriesNoTrailingSlash() throws IOException {
        for (Route route : routes()) {
            assertTrue(route.path().startsWith("/"), route + " kök eğik çizgiyle başlamalı");
            assertFalse(route.path().length() > 1 && route.path().endsWith("/"),
                    route + " sondaki eğik çizgi taşıyamaz");
        }
    }

    @Test
    void noTwoHandlersClaimTheSameVerbAndPath() throws IOException {
        Map<String, String> seen = new HashMap<>();
        for (Route route : routes()) {
            String key = route.verb() + " " + route.path();
            String previous = seen.put(key, route.controller());
            assertNull(previous, key + " iki yerde tanımlı: " + previous + " ve " + route.controller());
        }
    }

    @Test
    void everyAdminOnlyRuleLivesUnderTheAdminPrefix() throws IOException {
        for (String value : adminOnlyPaths(Files.readString(SECURITY_CONFIG))) {
            assertTrue(value.startsWith("/admin") || value.startsWith("/actuator"),
                    "ADMIN'e kısıtlı uç /admin altında olmalı: " + value);
        }
    }

    @Test
    void noCompanyScopedRuleIsLockedToThePlatformAdminRole() throws IOException {
        List<String> offenders = adminOnlyPaths(Files.readString(SECURITY_CONFIG)).stream()
                .filter(value -> value.startsWith("/companies") || value.startsWith("/contact-invitations"))
                .toList();

        assertTrue(offenders.isEmpty(),
                "Şirket yetkisi servis katmanında denetlenir, platform ADMIN rolüne kilitlenemez: " + offenders);
    }

    @Test
    void theAdminOnlyScannerSeesHasRole() {
        String config = """
                .requestMatchers(HttpMethod.POST, "/widgets").hasRole("ADMIN")
                """;

        assertEquals(List.of("/widgets"), adminOnlyPaths(config),
                "hasRole(\"ADMIN\") biçimi taramadan kaçamaz");
    }

    @Test
    void theAdminOnlyScannerSeesHasAnyRoleWhenAdminIsTheOnlyRole() {
        String config = """
                .requestMatchers(HttpMethod.POST, "/widgets").hasAnyRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/gadgets").hasAnyRole( "ADMIN" )
                """;

        assertEquals(List.of("/widgets", "/gadgets"), adminOnlyPaths(config),
                "tek rolü ADMIN olan hasAnyRole, hasRole(\"ADMIN\") ile aynı kısıttır");
    }

    @Test
    void theAdminOnlyScannerLeavesMultiRoleRulesAlone() {
        String config = """
                .requestMatchers(HttpMethod.POST, "/widgets").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/gadgets").hasAnyRole("ADMIN", "USER")
                """;

        assertEquals(List.of(), adminOnlyPaths(config),
                "çok rollü kural ADMIN'e kısıtlama değildir");
    }

    @Test
    void everyRouteUnderTheAdminPrefixIsRestrictedToAdmins() throws IOException {
        String config = Files.readString(SECURITY_CONFIG);
        assertTrue(config.contains(".requestMatchers(\"/admin/**\").hasRole(\"ADMIN\")"),
                "/admin/** kuralı SecurityConfig'te bulunmalı");

        assertFalse(routes().stream().noneMatch(route -> route.path().startsWith("/admin")),
                "en az bir admin ucu bekleniyordu");
    }

    @Test
    void dtoNamesDoNotEncodeTheHttpRouteOrVerb() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : dtoFiles()) {
            String name = file.getFileName().toString().replace(".java", "");
            for (String smell : List.of("GetResponse", "PostResponse", "PutResponse",
                    "PatchResponse", "DeleteResponse", "GetRequest", "PostRequest",
                    "PutRequest", "PatchRequest", "DeleteRequest")) {
                if (name.contains(smell)) {
                    offenders.add(name);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "DTO adı HTTP fiilini taşımamalı, niyeti anlatmalı: " + offenders);
    }

    @Test
    void dtoNamesStayReadable() throws IOException {
        List<String> tooLong = dtoFiles().stream()
                .map(f -> f.getFileName().toString().replace(".java", ""))
                .filter(name -> name.length() > 32)
                .toList();

        assertTrue(tooLong.isEmpty(), "DTO adı 32 karakteri aşmamalı: " + tooLong);
    }

    @Test
    void everyDtoLivesInARequestOrResponsePackage() throws IOException {
        List<String> misplaced = new ArrayList<>();

        for (Path file : dtoFiles()) {
            String parent = file.getParent().getFileName().toString();
            if (!List.of("request", "response", "common").contains(parent)) {
                misplaced.add(DTOS.relativize(file).toString());
            }
        }

        assertTrue(misplaced.isEmpty(),
                "DTO yalnızca request/response (ve common) altında durur: " + misplaced);
    }
}
