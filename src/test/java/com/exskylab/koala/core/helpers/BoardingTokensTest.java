package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardingTokensTest {

    private static final String SECRET = "unit-test-shuttle-secret";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2026-09-15T06:00:00Z");

    @Test
    void aMintedShuttleTokenVerifiesOnTheSameShuttleWithItsWindow() {
        UUID shuttleId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW.plusSeconds(37), TTL, false);

        BoardingTokens.Valid valid = assertInstanceOf(BoardingTokens.Valid.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, token, NOW.plusSeconds(37), TTL));

        assertEquals(false, valid.staticQr());
        assertEquals(NOW, valid.windowStart());
        assertEquals(NOW.plus(TTL), valid.windowEnd());
        assertTrue(token.startsWith("s1." + shuttleId + "."));
        assertEquals(4, token.split("\\.").length);
    }

    @Test
    void thePreviousWindowIsStillAccepted() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW.minusSeconds(1), TTL, false);

        BoardingTokens.Valid valid = assertInstanceOf(BoardingTokens.Valid.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, token, NOW, TTL));
        assertEquals(NOW, valid.windowEnd());
    }

    @Test
    void aStaleWindowIsExpiredNotInvalidAndKeepsItsWindow() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW.minus(Duration.ofMinutes(25)), TTL, false);

        BoardingTokens.Expired expired = assertInstanceOf(BoardingTokens.Expired.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, token, NOW, TTL));
        assertEquals(NOW.minus(Duration.ofMinutes(30)), expired.windowStart());
        assertEquals(NOW.minus(Duration.ofMinutes(20)), expired.windowEnd());
    }

    @Test
    void aFutureWindowIsExpiredNotInvalid() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW.plus(Duration.ofMinutes(25)), TTL, false);

        assertInstanceOf(BoardingTokens.Expired.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, token, NOW, TTL));
    }

    @Test
    void aTokenForAnotherShuttleIsInvalid() {
        String token = BoardingTokens.mintShuttle(SECRET, UUID.randomUUID(), NOW, TTL, false);

        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyShuttle(SECRET, UUID.randomUUID(), token, NOW, TTL));
    }

    @Test
    void aTamperedMacOrAnotherSecretIsInvalid() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW, TTL, false);
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, tampered, NOW, TTL));
        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyShuttle("rotated-secret", shuttleId, token, NOW, TTL));
    }

    @Test
    void aStaticTokenNeverExpiresAndHasNoWindow() {
        UUID shuttleId = UUID.randomUUID();
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, NOW, TTL, true);

        BoardingTokens.Valid later = assertInstanceOf(BoardingTokens.Valid.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, token, NOW.plus(Duration.ofDays(30)), TTL));

        assertTrue(later.staticQr());
        assertEquals(null, later.windowStart());
        assertTrue(token.contains(".static."));
    }

    @Test
    void aWorkerTokenVerifiesOnTheSameSeatOnly() {
        UUID seatId = UUID.randomUUID();
        String token = BoardingTokens.mintWorker(SECRET, seatId, NOW, TTL);

        assertInstanceOf(BoardingTokens.Valid.class, BoardingTokens.verifyWorker(SECRET, seatId, token, NOW, TTL));
        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyWorker(SECRET, UUID.randomUUID(), token, NOW, TTL));
        assertTrue(token.startsWith("w1." + seatId + "."));
    }

    @Test
    void aTokenOfOneKindNeverPassesForTheOtherEvenWithTheSameIdAndSecret() {
        UUID sharedId = UUID.randomUUID();
        String workerToken = BoardingTokens.mintWorker(SECRET, sharedId, NOW, TTL);
        String shuttleToken = BoardingTokens.mintShuttle(SECRET, sharedId, NOW, TTL, false);
        String staticToken = BoardingTokens.mintShuttle(SECRET, sharedId, NOW, TTL, true);

        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyShuttle(SECRET, sharedId, workerToken, NOW, TTL));
        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyWorker(SECRET, sharedId, shuttleToken, NOW, TTL));
        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyWorker(SECRET, sharedId, staticToken, NOW, TTL));
        String relabelled = "s1" + workerToken.substring(2);
        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyShuttle(SECRET, sharedId, relabelled, NOW, TTL));
    }

    @Test
    void garbageIsInvalid() {
        UUID shuttleId = UUID.randomUUID();
        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, "not-a-token", NOW, TTL));
        assertInstanceOf(BoardingTokens.Invalid.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, null, NOW, TTL));
    }

    @Test
    void expiresAtIsTheEndOfTheLastWindowThatStillAcceptsTheToken() {
        UUID shuttleId = UUID.randomUUID();
        Instant mintedAt = NOW.plusSeconds(37);
        String token = BoardingTokens.mintShuttle(SECRET, shuttleId, mintedAt, TTL, false);
        Instant until = BoardingTokens.acceptedUntil(mintedAt, TTL);
        Instant refresh = BoardingTokens.refreshAt(mintedAt, TTL);

        assertInstanceOf(BoardingTokens.Valid.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, token, until.minusSeconds(1), TTL));
        assertInstanceOf(BoardingTokens.Expired.class,
                BoardingTokens.verifyShuttle(SECRET, shuttleId, token, until, TTL));
        assertTrue(refresh.isAfter(mintedAt));
        assertEquals(TTL, Duration.between(refresh, until));
    }

    @Test
    void theLinkCarriesOnlyTheEnvironmentSchemeTheHostAndTheToken() {
        String token = BoardingTokens.mintWorker(SECRET, UUID.randomUUID(), NOW, TTL);

        assertEquals("koala-staging://board?t=" + token, BoardingTokens.link("koala-staging", token));
        assertEquals("koala://board?t=" + token, BoardingTokens.link("koala", token));
        assertTrue(token.matches("[A-Za-z0-9._-]+"), "token must survive a URL query unescaped: " + token);
    }
}
