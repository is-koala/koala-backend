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

class NoStartupDataSeedingTest {

    private static final Set<String> ALLOWED_RUNNERS = Set.of("AdminBootstrapRunner.java");

    @Test
    void productionCodeHasNoStartupRunner() throws IOException {
        List<String> offenders = startupRunners().stream()
                .filter(path -> !ALLOWED_RUNNERS.contains(path.getFileName().toString()))
                .map(Path::toString)
                .toList();

        assertTrue(offenders.isEmpty(),
                "Acilista veri yazabilecek runner eklenmis: " + offenders);
    }

    @Test
    void theAdminBootstrapRunnerIsStillTheOnlyExemption() throws IOException {
        List<String> allowed = startupRunners().stream()
                .map(path -> path.getFileName().toString())
                .filter(ALLOWED_RUNNERS::contains)
                .sorted()
                .toList();

        assertEquals(List.of("AdminBootstrapRunner.java"), allowed,
                "Muafiyet listesi artik gecerli degil, guncellenmeli (CLAUDE.md §2f3).");
    }

    private static List<Path> startupRunners() throws IOException {
        Path sources = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(sources)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(NoStartupDataSeedingTest::declaresStartupRunner)
                    .toList();
        }
    }

    private static boolean declaresStartupRunner(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("implements ApplicationRunner")
                    || source.contains("implements CommandLineRunner");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
