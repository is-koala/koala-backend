package com.exskylab.koala.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoStartupDataSeedingTest {

    @Test
    void productionCodeHasNoStartupRunner() throws IOException {
        Path sources = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(sources)) {
            List<String> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(NoStartupDataSeedingTest::declaresStartupRunner)
                    .map(Path::toString)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Acilista veri yazabilecek runner eklenmis: " + offenders);
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
