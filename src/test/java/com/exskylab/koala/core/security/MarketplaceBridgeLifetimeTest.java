package com.exskylab.koala.core.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceBridgeLifetimeTest {

    private static final Path SECURITY_CONFIG =
            Paths.get("src/main/java/com/exskylab/koala/core/configs/SecurityConfig.java");
    private static final Path FACADE =
            Paths.get("src/main/java/com/exskylab/koala/business/concretes/StaffPayoutManager.java");
    private static final Path AUTHORIZATION_MANAGER =
            Paths.get("src/main/java/com/exskylab/koala/core/security/StaffPermissionAuthorizationManager.java");

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
    void theHttpDoorAndTheMoneyFacadeAskTheSameGate() throws IOException {
        assertTrue(Files.readString(FACADE).contains(GATE),
                "aktörsüz para hareketi köprünün açık olmasına bağlı olmalı");
        assertTrue(Files.readString(AUTHORIZATION_MANAGER).contains(GATE),
                "marketplace ROLE_ADMIN'in /admin/** kapısı aynı anahtara bağlı olmalı; "
                        + "iki kapı ayrı anahtarlara bağlanırsa biri kesilirken diğeri açık kalır");
    }
}
