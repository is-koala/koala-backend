package com.exskylab.koala.core.configs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MailDeliveryEnvironmentContractTest {

    private static final Path ENVIRONMENTS = Path.of("docs/environments.md");

    private static final Path CLAUDE = Path.of("CLAUDE.md");

    @Test
    void stagingDocumentationSaysUnrestrictedMailStopsStartup() throws IOException {
        String staging = section(ENVIRONMENTS, "## Staging zorunlulukları");

        assertTrue(staging.contains("`MAIL_DELIVERY_MODE=ALLOWLIST`"));
        assertTrue(staging.contains("UNRESTRICTED"));
        assertTrue(staging.contains("durdurur"));
        assertTrue(staging.contains(MailDeliveryEnvironmentGuard.MARKER));
    }

    @Test
    void claudeRecordsTheMailGuardWithoutCopyingRecipientAddresses() throws IOException {
        String section = Files.readString(CLAUDE, StandardCharsets.UTF_8);
        int start = section.indexOf("## 2ay.");
        assertTrue(start >= 0, "CLAUDE.md §2ay mail teslimat kapısını anlatmalı");
        int end = section.indexOf("\n## ", start + 4);
        String body = end < 0 ? section.substring(start) : section.substring(start, end);

        assertTrue(body.contains(MailDeliveryEnvironmentGuard.MARKER));
        assertTrue(body.contains("MAIL_DELIVERY_MODE"));
        assertTrue(body.contains("MAIL_ALLOWED_RECIPIENTS"));
        assertTrue(body.contains("durdurur"));
        assertTrue(body.contains("uyarır"));
        assertTrue(body.lines().noneMatch(line -> line.contains("@") && !line.contains("`MAIL_ALLOWED_RECIPIENTS`")));
    }

    private static String section(Path document, String heading) throws IOException {
        String text = Files.readString(document, StandardCharsets.UTF_8);
        int start = text.indexOf(heading);
        assertTrue(start >= 0, document + " içinde " + heading + " yok");
        int end = text.indexOf("\n## ", start + heading.length());
        return end < 0 ? text.substring(start) : text.substring(start, end);
    }
}
