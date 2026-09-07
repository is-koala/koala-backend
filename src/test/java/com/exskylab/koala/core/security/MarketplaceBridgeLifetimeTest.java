package com.exskylab.koala.core.security;

import com.exskylab.koala.core.properties.StaffBridgeProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarketplaceBridgeLifetimeTest {

    private static final Path SECURITY_CONFIG =
            Paths.get("src/main/java/com/exskylab/koala/core/configs/SecurityConfig.java");
    private static final Path FACADE =
            Paths.get("src/main/java/com/exskylab/koala/business/concretes/StaffPayoutManager.java");

    private static final String BRIDGE_RULE = ".requestMatchers(\"/admin/**\").hasRole(\"ADMIN\")";
    private static final String ACTORLESS_BRANCH = "if (actor == null) {";
    private static final String GATE = "staffBridgeProperties.isOpen()";

    @Test
    void theActorlessMoneyBranchLivesExactlyAsLongAsTheMarketplaceBridge() throws IOException {
        boolean bridgeIsStillWired = Files.readString(SECURITY_CONFIG).contains(BRIDGE_RULE);
        boolean actorlessBranchIsStillThere = Files.readString(FACADE).contains(ACTORLESS_BRANCH);

        assertEquals(bridgeIsStillWired, actorlessBranchIsStillThere,
                "ADR-0010 §Geçiş'in kesimi SecurityConfig'teki marketplace ROLE_ADMIN satırının silinmesidir. "
                        + "O satır gittiyse StaffPayoutManager'daki aktörsüz dal da gitmelidir: kesimden sonra "
                        + "hiçbir para hareketi personel izni denetlenmeden çalışamaz.");
    }

    @Test
    void theCutHasHappenedAndNeitherHalfCameBack() throws IOException {
        assertFalse(Files.readString(SECURITY_CONFIG).contains(BRIDGE_RULE),
                "marketplace ROLE_ADMIN'in /admin/** satırı kesimle birlikte silindi (#98)");
        assertFalse(Files.readString(FACADE).contains(ACTORLESS_BRANCH),
                "aktörsüz para dalı kesimle birlikte silindi (#98)");
        assertFalse(Files.readString(FACADE).contains(GATE),
                "para facade'ı artık köprü anahtarını hiç okumamalı; aktörsüz istek koşulsuz reddedilir");
    }

    @Test
    void theBridgeIsClosedByDefaultAndOnlyTheHttpDoorCanBeReopened() throws IOException {
        assertFalse(new StaffBridgeProperties().isOpen(),
                "köprü varsayılanı kapalı olmalı: personel girişi indi (ADR-0014 §Sonuçlar)");

        String door = Files.readString(
                Paths.get("src/main/java/com/exskylab/koala/core/security/"
                        + "StaffPermissionAuthorizationManager.java"));

        assertEquals(true, door.contains(GATE),
                "geri açılabilen tek kapı /admin/**'ın marketplace ROLE_ADMIN kapısı; para dalı geri açılamaz");
    }
}
