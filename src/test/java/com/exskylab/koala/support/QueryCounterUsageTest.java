package com.exskylab.koala.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCounterUsageTest {

    private static final Path TEST_SOURCES = Path.of("src", "test", "java");

    private static final Set<String> MAY_READ_GLOBAL_STATISTICS = Set.of("QueryCounter.java", "QueryCounterIT.java",
            "QueryCounterUsageTest.java");

    private static List<Path> testSources() throws IOException {
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void noTestCountsQueriesWithTheSessionFactoryWideStatistics() throws IOException {
        List<String> offenders = testSources().stream()
                .filter(path -> !MAY_READ_GLOBAL_STATISTICS.contains(path.getFileName().toString()))
                .filter(path -> {
                    String source = read(path);
                    return source.contains("getStatistics()") || source.contains("import org.hibernate.stat.");
                })
                .map(path -> path.getFileName().toString())
                .toList();

        assertEquals(List.of(), offenders,
                "Hibernate Statistics SessionFactory geneli; arka plan işlerinin sorgularını da sayar ve N+1 "
                        + "testlerini rastgele kırmızıya düşürür (#356). Sorgu sayımı QueryCounter.start ile yapılır.");
    }

    @Test
    void theScanSeesTheQueryCountingTests() throws IOException {
        long users = testSources().stream()
                .filter(path -> read(path).contains("QueryCounter.start("))
                .count();

        assertTrue(users >= 11, "QueryCounter kullanan dosya sayısı " + users
                + "; tarama yanlış dizinde koşuyorsa yukarıdaki kural boşa geçer");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(path.toString(), e);
        }
    }
}
