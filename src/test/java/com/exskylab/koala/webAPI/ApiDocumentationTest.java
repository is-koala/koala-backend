package com.exskylab.koala.webAPI;

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

class ApiDocumentationTest {

    private static final Path CONTROLLERS = Paths.get("src/main/java/com/exskylab/koala/webAPI");

    private static final Pattern MAPPING =
            Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping");

    private static final int ANNOTATION_WINDOW = 14;

    private record Endpoint(String file, int line, String mapping) {
    }

    private static List<Endpoint> undocumented(String required) throws IOException {
        List<Endpoint> found = new ArrayList<>();

        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                List<String> lines = Files.readAllLines(file);

                for (int i = 0; i < lines.size(); i++) {
                    if (!MAPPING.matcher(lines.get(i)).find()) {
                        continue;
                    }

                    String window = String.join("\n", lines.subList(Math.max(0, i - ANNOTATION_WINDOW), i));
                    if (!window.contains(required)) {
                        found.add(new Endpoint(file.getFileName().toString(), i + 1, lines.get(i).trim()));
                    }
                }
            }
        }
        return found;
    }

    @Test
    void everyEndpointCarriesAnOperationSummary() throws IOException {
        List<Endpoint> missing = undocumented("@Operation");

        assertTrue(missing.isEmpty(), "dokumantasyonsuz uc: " + missing.stream()
                .map(e -> e.file() + ":" + e.line() + " " + e.mapping())
                .toList());
    }

    @Test
    void everyEndpointDeclaresItsResponses() throws IOException {
        List<Endpoint> missing = undocumented("@ApiResponse");

        assertTrue(missing.isEmpty(), "yanit kodu belirtilmemis uc: " + missing.stream()
                .map(e -> e.file() + ":" + e.line() + " " + e.mapping())
                .toList());
    }

    @Test
    void theDocumentedAuthorisationMatchesTheSecurityConfiguration() throws IOException {
        String security = Files.readString(
                Paths.get("src/main/java/com/exskylab/koala/core/configs/SecurityConfig.java"))
                + Files.readString(
                Paths.get("src/main/java/com/exskylab/koala/core/configs/StaffSecurityConfig.java"));

        List<String> wrong = new ArrayList<>();

        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                List<String> lines = Files.readAllLines(file);
                String base = basePathOf(lines);

                for (int i = 0; i < lines.size(); i++) {
                    if (!MAPPING.matcher(lines.get(i)).find()) {
                        continue;
                    }

                    String path = base + pathOf(lines.get(i));
                    String verb = verbOf(lines.get(i));
                    String window = String.join("\n", lines.subList(Math.max(0, i - ANNOTATION_WINDOW), i));

                    boolean documentedAsPublic = window.contains("@SecurityRequirements");
                    boolean actuallyPublic = isPermitAll(security, verb, path);

                    if (documentedAsPublic != actuallyPublic) {
                        wrong.add("%s:%d %s %s dokumanda %s, guvenlik zincirinde %s"
                                .formatted(file.getFileName(), i + 1, verb, path,
                                        documentedAsPublic ? "acik" : "korumali",
                                        actuallyPublic ? "acik" : "korumali"));
                    }
                }
            }
        }

        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    private static String basePathOf(List<String> lines) {
        return lines.stream()
                .filter(line -> line.startsWith("@RequestMapping("))
                .map(ApiDocumentationTest::pathOf)
                .findFirst()
                .orElse("");
    }

    private static String pathOf(String line) {
        Matcher quoted = Pattern.compile("\"([^\"]*)\"").matcher(line);
        return quoted.find() ? quoted.group(1) : "";
    }

    private static String verbOf(String line) {
        Matcher mapping = MAPPING.matcher(line);
        return mapping.find() ? mapping.group(1).toUpperCase(java.util.Locale.ROOT) : "";
    }

    private static boolean isPermitAll(String securityConfig, String verb, String path) {
        String normalised = path.replaceAll("\\{[^}]*\\}", "{}");

        for (String line : securityConfig.split("\n")) {
            if (!line.contains(".permitAll()")) {
                continue;
            }

            Matcher ruleVerb = Pattern.compile("HttpMethod\\.(\\w+)").matcher(line);
            if (ruleVerb.find() && !ruleVerb.group(1).equals(verb)) {
                continue;
            }

            Matcher quoted = Pattern.compile("\"([^\"]*)\"").matcher(line);
            while (quoted.find()) {
                String rule = quoted.group(1).replaceAll("\\{[^}]*\\}", "{}");

                if (rule.equals(normalised)) {
                    return true;
                }
                if (rule.endsWith("/**") && normalised.startsWith(rule.substring(0, rule.length() - 3))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void everyControllerCarriesATag() throws IOException {
        List<String> missing = new ArrayList<>();

        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file);
                if (MAPPING.matcher(source).find() && !source.contains("@Tag(")) {
                    missing.add(file.getFileName().toString());
                }
            }
        }

        assertTrue(missing.isEmpty(), "@Tag tasimayan controller: " + missing);
    }
}
