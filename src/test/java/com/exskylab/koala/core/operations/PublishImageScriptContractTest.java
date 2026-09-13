package com.exskylab.koala.core.operations;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PublishImageScriptContractTest {

    private static final Path SCRIPT = Path.of("scripts/publish-ghcr-image.sh");

    @Test
    void theMacScriptPushesASingleAmd64ManifestToTheProductionRepository() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(script.contains("ghcr.io/yustyy/koala")),
                () -> assertTrue(script.contains("--platform linux/amd64")),
                () -> assertTrue(script.contains("--provenance=false")),
                () -> assertTrue(script.contains("--sbom=false")),
                () -> assertTrue(script.contains("--push")),
                () -> assertTrue(script.contains("--latest")));
    }
}
