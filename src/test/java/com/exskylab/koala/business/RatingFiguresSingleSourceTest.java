package com.exskylab.koala.business;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RatingFiguresSingleSourceTest {

    private static final Path MAIN = Paths.get("src/main/java");

    private static final Pattern RAW_FIELD_READ =
            Pattern.compile("\\.(getAverageRating|getReviewCount)\\s*\\(");

    private static final Pattern ENTITY_AS_MAPPING_SOURCE =
            Pattern.compile("[(,]\\s*RatingSummary\\s+\\w+\\s*[,)]");

    private static final Map<String, String> ALLOWED_READERS = Map.of(
            "RatingSummaryManager",
            "özetin tek sahibi: record() biriktirir, figuresOf() profil biçimini (özet ya da ortalama yoksa 0/0) "
                    + "kurar; PublicProfileDto, EmployerStatsDto ve UserMeResponseDto yalnız oradan okur",
            "BadgeManager",
            "rozet eşiği ham özete bakar: yorum sayısı ve ortalama eşiği; özet ya da ortalama yoksa rozet verilmez, "
                    + "profil biçiminin 0/0'ı burada yanlış bir 'eşiğin altında' cevabı olurdu",
            "ReviewMapper",
            "GET /users/{id}/rating-summary ham özeti döndürür (özet yokken null) — MapStruct alanları üretilen "
                    + "kodda okur, bu yüzden kaynakta getter değil RatingSummary parametresi görünür");

    private static Set<String> rawReaders() throws IOException {
        Set<String> readers = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                if (name.equals("RatingSummary")) {
                    continue;
                }
                if (readsRawFields(Files.readString(file))) {
                    readers.add(name);
                }
            }
        }
        return readers;
    }

    private static boolean readsRawFields(String source) {
        return RAW_FIELD_READ.matcher(source).find() || ENTITY_AS_MAPPING_SOURCE.matcher(source).find();
    }

    @Test
    void onlyTheDocumentedPlacesReadTheRawRatingFields() throws IOException {
        List<String> intruders = rawReaders().stream()
                .filter(reader -> !ALLOWED_READERS.containsKey(reader))
                .toList();

        assertTrue(intruders.isEmpty(),
                "Puan rakamları RatingSummaryService.figuresOf'tan okunmalı; ham getAverageRating/getReviewCount "
                        + "okuyan yeni yer profil biçimini ikinci kez kurar ve uçlar ayrışır. Gerçekten gerekiyorsa "
                        + "ALLOWED_READERS'a gerekçesiyle yazılmalı: " + intruders);
    }

    @Test
    void everyAllowedReaderStillReadsTheRawFields() throws IOException {
        Set<String> readers = rawReaders();
        List<String> stale = ALLOWED_READERS.keySet().stream()
                .filter(allowed -> !readers.contains(allowed))
                .sorted()
                .toList();

        assertTrue(stale.isEmpty(), "Artık ham alan okumayan izin listede duruyor, çıkarılmalı: " + stale);
    }

    @Test
    void everyAllowedReaderCarriesAWrittenReason() {
        assertTrue(ALLOWED_READERS.values().stream().noneMatch(String::isBlank),
                "Gerekçesiz izin: " + ALLOWED_READERS);
    }

    @Test
    void theScanSeesGetterCallsAndEntityMappingSourcesButNotTheProfileShapes() {
        assertTrue(readsRawFields("return summary.getAverageRating();"));
        assertTrue(readsRawFields("int count = summary .getReviewCount ();"));
        assertTrue(readsRawFields("RatingSummaryDto toRatingSummaryDto(RatingSummary summary);"));
        assertTrue(readsRawFields("Dto toDto(User user, RatingSummary summary);"));

        assertFalse(readsRawFields("rating.averageRating(), rating.reviewCount()"));
        assertFalse(readsRawFields("RatingFigures rating = ratingSummaryService.figuresOf(userId);"));
        assertFalse(readsRawFields("Optional<RatingSummary> findFor(UUID userId);"));
    }
}
