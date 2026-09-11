package com.exskylab.koala.core.utilities.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassiveAuthenticationPrepTest {

    @Test
    void shippedYamlDefaultsPassiveAuthenticationToFalse() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"),
                StandardCharsets.UTF_8);

        assertTrue(yaml.contains("IDENTITY_PASSIVE_AUTH_ENABLED:false"),
                "bayrak yaml varsayilaninda kapali kalmali");
        assertTrue(yaml.contains("IDENTITY_CSCA_DIRECTORY:"),
                "ek cipa dizini opsiyonel kalmali");
        assertFalse(yaml.contains("IDENTITY_PASSIVE_AUTH_ENABLED:true"),
                "uretim yaml'i bayragi acmamali");
    }

    @Test
    void testResourcesDoNotEnablePassiveAuthentication() throws IOException {
        Path root = Path.of("src/test/resources");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> hits = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".properties")
                                || name.endsWith(".yaml")
                                || name.endsWith(".yml");
                    })
                    .filter(path -> {
                        try {
                            String text = Files.readString(path, StandardCharsets.UTF_8);
                            return text.contains("IDENTITY_PASSIVE_AUTH_ENABLED=true")
                                    || text.contains("IDENTITY_PASSIVE_AUTH_ENABLED: true")
                                    || text.contains("identity.passive-authentication.enabled=true");
                        } catch (IOException exception) {
                            throw new IllegalStateException(path.toString(), exception);
                        }
                    })
                    .toList();

            assertTrue(hits.isEmpty(),
                    "test kaynaklari bayragi acmamali; cip kaniti uretmeyen her test kirilir: "
                            + hits);
        }
    }

    @Test
    void theCscaFetchScriptIsInTheRepository() throws IOException {
        Path script = Path.of("scripts/fetch-csca-anchors.sh");

        assertTrue(Files.isRegularFile(script),
                "scripts/fetch-csca-anchors.sh git'te izlenmeli; aksi halde liste yenilemesi "
                        + "tek kisiye bagli kalir");
        String body = Files.readString(script, StandardCharsets.UTF_8);
        assertTrue(body.contains("bsi.bund.de"),
                "betik BSI master list'ini indirmeli");
        assertTrue(body.contains("C={country}") || body.contains("C = {country}"),
                "betik ulke koduna gore C=TR sertifikalarini yazmali");
    }
}
