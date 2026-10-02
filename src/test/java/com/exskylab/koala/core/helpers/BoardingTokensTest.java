package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardingTokensTest {

    private static final String SECRET = "unit-test-shuttle-secret";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2026-09-15T06:00:00Z");

    @Test
    void aMintedShuttleTokenVerifiesOnTheSameShuttle() {
        UUID shuttleId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW, TTL, false);

        BoardingTokens.Verified verified = BoardingTokens.verifyShuttle(SECRET, shuttleId, token, NOW, TTL);

        assertFalse(verified.staticQr());
        assertTrue(token.startsWith("s1." + shuttleId + "."));
        assertEquals(4, token.split("\\.").length);
    }

    @Test
    void thePreviousWindowIsStillAccepted() {
        UUID shuttleId = UUID.randomUUID();
        Instant startOfWindow = Instant.ofEpochSecond(Math.floorDiv(NOW.getEpochSecond(), TTL.toSeconds())
                * TTL.toSeconds());
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, startOfWindow.minusSeconds(1), TTL, false);

        BoardingTokens.verifyShuttle(SECRET, shuttleId, token, startOfWindow, TTL);
    }

    @Test
    void aStaleWindowIsExpiredNotInvalid() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW.minus(Duration.ofMinutes(25)), TTL, false);

        assertThrows(BoardingTokens.ExpiredToken.class,
                () -> BoardingTokens.verifyShuttle(SECRET, shuttleId, token, NOW, TTL));
    }

    @Test
    void aFutureWindowIsExpiredNotInvalid() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW.plus(Duration.ofMinutes(25)), TTL, false);

        assertThrows(BoardingTokens.ExpiredToken.class,
                () -> BoardingTokens.verifyShuttle(SECRET, shuttleId, token, NOW, TTL));
    }

    @Test
    void aTokenForAnotherShuttleIsInvalid() {
        UUID mintedFor = UUID.randomUUID();
        UUID presentedAs = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, mintedFor, NOW, TTL, false);

        assertThrows(BoardingTokens.InvalidToken.class,
                () -> BoardingTokens.verifyShuttle(SECRET, presentedAs, token, NOW, TTL));
    }

    @Test
    void aTamperedMacIsInvalid() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW, TTL, false);
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThrows(BoardingTokens.InvalidToken.class,
                () -> BoardingTokens.verifyShuttle(SECRET, shuttleId, tampered, NOW, TTL));
    }

    @Test
    void aStaticTokenNeverExpiresAndIsFlagged() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW, TTL, true);

        BoardingTokens.Verified later = BoardingTokens.verifyShuttle(SECRET, shuttleId, token,
                NOW.plus(Duration.ofDays(30)), TTL);

        assertTrue(later.staticQr());
        assertTrue(token.contains(".static."));
    }

    @Test
    void aWorkerTokenVerifiesOnTheSameSeat() {
        UUID seatId = UUID.randomUUID();
        String token = BoardingTokens.mintWorker(SECRET, seatId, NOW, TTL);

        BoardingTokens.verifyWorker(SECRET, seatId, token, NOW, TTL);
        assertTrue(token.startsWith("w1." + seatId + "."));
    }

    @Test
    void aWorkerTokenForAnotherSeatIsInvalid() {
        UUID seatId = UUID.randomUUID();
        String token = BoardingTokens.mintWorker(SECRET, seatId, NOW, TTL);

        assertThrows(BoardingTokens.InvalidToken.class,
                () -> BoardingTokens.verifyWorker(SECRET, UUID.randomUUID(), token, NOW, TTL));
    }

    @Test
    void garbageIsInvalid() {
        UUID shuttleId = UUID.randomUUID();
        assertThrows(BoardingTokens.InvalidToken.class,
                () -> BoardingTokens.verifyShuttle(SECRET, shuttleId, "not-a-token", NOW, TTL));
        assertThrows(BoardingTokens.InvalidToken.class,
                () -> BoardingTokens.verifyShuttle(SECRET, shuttleId, null, NOW, TTL));
    }
}
