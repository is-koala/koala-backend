package com.exskylab.koala.core.helpers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public final class BoardingTokens {

    private static final String SHUTTLE_PREFIX = "s1";
    private static final String WORKER_PREFIX = "w1";
    private static final String STATIC_WINDOW = "static";
    private static final String LINK_HOST = "board";

    private BoardingTokens() {
    }

    public sealed interface Verdict permits Valid, Expired, Invalid {
    }

    public record Valid(boolean staticQr, Instant windowStart, Instant acceptedUntil) implements Verdict {
    }

    public record Expired(Instant windowStart, Instant acceptedUntil) implements Verdict {
    }

    public record Invalid() implements Verdict {
    }

    public static String mintShuttle(String secret, UUID shuttleId, Instant now, Duration ttl, boolean staticQr) {
        String payload = staticQr ? STATIC_WINDOW : Long.toString(windowOf(now, ttl));
        return mint(SHUTTLE_PREFIX, secret, shuttleId, payload);
    }

    public static String mintWorker(String secret, UUID seatId, Instant now, Duration ttl) {
        return mint(WORKER_PREFIX, secret, seatId, Long.toString(windowOf(now, ttl)));
    }

    public static String link(String appScheme, String token) {
        return appScheme + "://" + LINK_HOST + "?t=" + token;
    }

    public static Instant refreshAt(Instant now, Duration ttl) {
        return windowStart(windowOf(now, ttl) + 1, ttl);
    }

    public static Instant acceptedUntil(Instant now, Duration ttl) {
        return windowStart(windowOf(now, ttl) + 2, ttl);
    }

    public static Verdict verifyShuttle(String secret, UUID shuttleId, String token, Instant now, Duration ttl) {
        return verify(SHUTTLE_PREFIX, secret, shuttleId, token, now, ttl, true);
    }

    public static Verdict verifyWorker(String secret, UUID seatId, String token, Instant now, Duration ttl) {
        return verify(WORKER_PREFIX, secret, seatId, token, now, ttl, false);
    }

    private static Verdict verify(String prefix, String secret, UUID expectedId, String token, Instant now,
                                  Duration ttl, boolean allowStatic) {
        if (token == null || token.isBlank()) {
            return new Invalid();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !prefix.equals(parts[0]) || !expectedId.toString().equals(parts[1])) {
            return new Invalid();
        }
        String payload = parts[2];
        boolean staticQr = STATIC_WINDOW.equals(payload);
        if (staticQr && !allowStatic) {
            return new Invalid();
        }
        String expectedMac = mac(secret, canonical(prefix, expectedId, payload));
        if (!MessageDigest.isEqual(expectedMac.getBytes(StandardCharsets.US_ASCII),
                parts[3].getBytes(StandardCharsets.US_ASCII))) {
            return new Invalid();
        }
        if (staticQr) {
            return new Valid(true, null, null);
        }
        long presented;
        try {
            presented = Long.parseLong(payload);
        } catch (NumberFormatException exception) {
            return new Invalid();
        }
        Instant start = windowStart(presented, ttl);
        Instant end = windowStart(presented + 2, ttl);
        long current = windowOf(now, ttl);
        if (presented == current || presented == current - 1) {
            return new Valid(false, start, end);
        }
        return new Expired(start, end);
    }

    private static String mint(String prefix, String secret, UUID id, String payload) {
        return prefix + "." + id + "." + payload + "." + mac(secret, canonical(prefix, id, payload));
    }

    private static String canonical(String prefix, UUID id, String payload) {
        return prefix + "|" + id + "|" + payload;
    }

    private static long windowOf(Instant now, Duration ttl) {
        return Math.floorDiv(now.getEpochSecond(), windowSeconds(ttl));
    }

    private static Instant windowStart(long window, Duration ttl) {
        return Instant.ofEpochSecond(Math.multiplyExact(window, windowSeconds(ttl)));
    }

    private static long windowSeconds(Duration ttl) {
        return Math.max(1L, ttl.toSeconds());
    }

    private static String mac(String secret, String canonical) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
