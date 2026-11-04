package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.badge.request.SaveBadgeDto;
import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.core.dtos.gallery.request.GalleryImageEntryDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Primitive request fields become {@code false}/{@code 0} when omitted from JSON.
 * Wrap them, or document a conscious default here so the next field cannot
 * sneak in. Response DTOs are out of scope (#179).
 */
class RequestDtoPrimitiveContractTest {

    private static final Path DTOS = Path.of("src/main/java/com/exskylab/koala/core/dtos");
    private static final Pattern PRIMITIVE_FIELD = Pattern.compile(
            "private\\s+(boolean|int|long|double|float)\\s+(\\w+)\\s*;");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> DOCUMENTED_PRIMITIVE_DEFAULTS = Set.of();

    private final Validator validator;

    RequestDtoPrimitiveContractTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void aPrimitiveFieldIsReportedByTheInventory() {
        String source = """
                public class ExampleRequestDto {
                    private boolean cover;
                    private int sortOrder;
                }
                """;

        assertTrue(primitiveFieldsIn("ExampleRequestDto", source)
                .containsAll(Set.of("ExampleRequestDto.cover", "ExampleRequestDto.sortOrder")));
    }

    @Test
    void remainingRequestDtoPrimitivesAreWrappedOrDocumented() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(DTOS)) {
            for (Path file : files.filter(this::isRequestDto).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                found.addAll(primitiveFieldsIn(name, Files.readString(file, StandardCharsets.UTF_8)));
            }
        }

        List<String> undocumented = found.stream()
                .filter(key -> !DOCUMENTED_PRIMITIVE_DEFAULTS.contains(key))
                .toList();
        List<String> stale = DOCUMENTED_PRIMITIVE_DEFAULTS.stream()
                .filter(key -> !found.contains(key))
                .sorted()
                .toList();

        assertTrue(undocumented.isEmpty(),
                "Primitive request field silently defaults. Wrap Boolean/Integer + @NotNull "
                        + "or allowlist with a reason: " + undocumented);
        assertTrue(stale.isEmpty(),
                "Allowlist went stale; field was wrapped or removed: " + stale);
    }

    @Test
    void aGalleryEntryMissingCoverAndSortOrderIsRejectedNotDefaulted() {
        var dto = JSON.readValue("{\"imageId\":\"11111111-1111-1111-1111-111111111111\"}",
                GalleryImageEntryDto.class);

        assertNull(dto.getCover(), "eksik cover sessizce false olmamalı");
        assertNull(dto.getSortOrder(), "eksik sortOrder sessizce 0 olmamalı");
        assertTrue(violatedFields(dto).containsAll(Set.of("cover", "sortOrder")));
    }

    @Test
    void galleryCoverAndSortOrderRoundTripUnderTheirOwnJsonNames() {
        var dto = JSON.readValue("""
                {"imageId":"11111111-1111-1111-1111-111111111111","sortOrder":2,"cover":true}
                """, GalleryImageEntryDto.class);

        assertEquals(Boolean.TRUE, dto.getCover());
        assertEquals(2, dto.getSortOrder());
        assertTrue(JSON.writeValueAsString(dto).contains("\"cover\":true"));
        assertTrue(JSON.writeValueAsString(dto).contains("\"sortOrder\":2"));
    }

    @Test
    void anEducationMissingGpaIsRejectedNotDefaultedToZero() {
        var dto = JSON.readValue("{}", SaveEducationDto.class);

        assertNull(dto.getGpa(), "eksik gpa sessizce 0.0 olmamalı");
        assertTrue(violatedFields(dto).contains("gpa"));
    }

    @Test
    void gpaRoundTripsUnderItsOwnJsonName() {
        var dto = JSON.readValue("{\"gpa\":3.4}", SaveEducationDto.class);

        assertEquals(3.4, dto.getGpa());
        assertTrue(JSON.writeValueAsString(dto).contains("\"gpa\":3.4"));
    }

    @Test
    void aBadgeMissingSortOrderIsRejectedNotDefaulted() {
        var dto = JSON.readValue("{}", SaveBadgeDto.class);

        assertNull(dto.getSortOrder(), "eksik sortOrder sessizce 0 olmamalı");
        assertTrue(violatedFields(dto).contains("sortOrder"));
    }

    @Test
    void badgeSortOrderRoundTripsUnderItsOwnJsonName() {
        var dto = JSON.readValue("{\"sortOrder\":7}", SaveBadgeDto.class);

        assertEquals(7, dto.getSortOrder());
        assertTrue(JSON.writeValueAsString(dto).contains("\"sortOrder\":7"));
    }

    private boolean isRequestDto(Path file) {
        String path = file.toString().replace('\\', '/');
        return path.endsWith(".java") && path.contains("/request/");
    }

    static Set<String> primitiveFieldsIn(String type, String source) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PRIMITIVE_FIELD.matcher(source);
        while (matcher.find()) {
            found.add(type + "." + matcher.group(2));
        }
        return found;
    }

    private Set<String> violatedFields(Object dto) {
        return validator.validate(dto).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
