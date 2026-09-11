package com.exskylab.koala.core.dtos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoColumnWidthRulesTest {

    private static final Path DTOS = Path.of("src/main/java/com/exskylab/koala/core/dtos");
    private static final Path ENTITIES = Path.of("src/main/java/com/exskylab/koala/entities");

    private static final Pattern SIZE_STRING_FIELD = Pattern.compile(
            "@Size\\s*\\(([^)]*)\\)((?:\\s*\\n\\s*@[^\\n]+)*)\\s*\\n\\s*private\\s+String\\s+(\\w+)\\s*;");

    private static final Pattern COLUMN_STRING_FIELD = Pattern.compile(
            "@Column\\s*\\(([^)]*)\\)((?:\\s*\\n\\s*@[^\\n]+)*)\\s*\\n\\s*private\\s+String\\s+(\\w+)\\s*;");

    @Test
    void aWiderSizeThanTheColumnIsAMismatch() {
        String dto = """
                @Size(max = 4000)
                private String description;
                """;
        String entity = """
                @Column(name = "description")
                private String description;
                """;

        List<String> mismatches = mismatchesIn(dto, entity);

        assertEquals(List.of("description Size 4000 > column 255"), mismatches);
    }

    @Test
    void equalWidthsAreNotMismatches() {
        String dto = """
                @Size(max = 512)
                private String website;
                """;
        String entity = """
                @Column(name = "website", length = 512)
                private String website;
                """;

        assertTrue(mismatchesIn(dto, entity).isEmpty());
    }

    @Test
    void requestDtoSizesDoNotExceedTheMatchingEntityColumn() throws IOException {
        List<String> mismatches = new ArrayList<>();

        for (Path dtoFile : requestDtoFiles()) {
            String domain = dtoFile.getParent().getParent().getFileName().toString();
            Path entityFile = ENTITIES.resolve(pascal(domain) + ".java");
            if (!Files.exists(entityFile)) {
                continue;
            }

            Map<String, Integer> columns = columnWidths(Files.readString(entityFile, StandardCharsets.UTF_8));
            Map<String, Integer> sizes = sizeWidths(Files.readString(dtoFile, StandardCharsets.UTF_8));

            for (Map.Entry<String, Integer> size : sizes.entrySet()) {
                Integer column = columns.get(size.getKey());
                if (column == null) {
                    continue;
                }
                if (size.getValue() > column) {
                    mismatches.add(dtoFile.getFileName() + "." + size.getKey()
                            + " Size " + size.getValue() + " > " + entityFile.getFileName()
                            + " column " + column);
                }
            }
        }

        assertTrue(mismatches.isEmpty(),
                "@Size, sutundan genis; 500 yerine 400 icin daralt veya sutunu genislet:\n"
                        + String.join("\n", mismatches));
    }

    static List<String> mismatchesIn(String dtoSource, String entitySource) {
        Map<String, Integer> columns = columnWidths(entitySource);
        Map<String, Integer> sizes = sizeWidths(dtoSource);
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, Integer> size : sizes.entrySet()) {
            Integer column = columns.get(size.getKey());
            if (column != null && size.getValue() > column) {
                mismatches.add(size.getKey() + " Size " + size.getValue() + " > column " + column);
            }
        }
        return mismatches;
    }

    static Map<String, Integer> sizeWidths(String source) {
        Map<String, Integer> widths = new LinkedHashMap<>();
        Matcher matcher = SIZE_STRING_FIELD.matcher(source);
        while (matcher.find()) {
            Integer max = intAttribute(matcher.group(1), "max");
            if (max != null) {
                widths.put(matcher.group(3), max);
            }
        }
        return widths;
    }

    static Map<String, Integer> columnWidths(String source) {
        Map<String, Integer> widths = new LinkedHashMap<>();
        Matcher matcher = COLUMN_STRING_FIELD.matcher(source);
        while (matcher.find()) {
            String attributes = matcher.group(1);
            if (attributes.toLowerCase(Locale.ROOT).contains("text")) {
                continue;
            }
            Integer length = intAttribute(attributes, "length");
            widths.put(matcher.group(3), length == null ? 255 : length);
        }
        return widths;
    }

    private static Integer intAttribute(String attributes, String name) {
        Matcher matcher = Pattern.compile("\\b" + name + "\\s*=\\s*(\\d+)").matcher(attributes);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static String pascal(String camel) {
        if (camel.isEmpty()) {
            return camel;
        }
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    private List<Path> requestDtoFiles() throws IOException {
        try (Stream<Path> files = Files.walk(DTOS)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getParent() != null
                            && "request".equals(path.getParent().getFileName().toString()))
                    .toList();
        }
    }
}
