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

    private BoardingTokens() {
    }

    public static final class ExpiredToken extends RuntimeException {
    }

    public static final class InvalidToken extends RuntimeException {
    }

    public record Verified(boolean staticQr) {
    }

    public static String mintShuttle(String secret, UUID shuttleId, Instant now, Duration ttl, boolean staticQr) {
        long window = windowOf(now, ttl);
        String payload = staticQr ? STATIC_WINDOW : Long.toString(window);
        return mint(SHUTTLE_PREFIX, secret, shuttleId, payload);
    }

    public static String mintWorker(String secret, UUID seatId, Instant now, Duration ttl) {
        return mint(WORKER_PREFIX, secret, seatId, Long.toString(windowOf(now, ttl)));
    }

    public static Verified verifyShuttle(String secret, UUID shuttleId, String token, Instant now, Duration ttl) {
        return verify(SHUTTLE_PREFIX, secret, shuttleId, token, now, ttl, true);
    }

    public static Verified verifyWorker(String secret, UUID seatId, String token, Instant now, Duration ttl) {
        return verify(WORKER_PREFIX, secret, seatId, token, now, ttl, false);
    }

    private static Verified verify(String prefix, String secret, UUID expectedId, String token, Instant now,
                                   Duration ttl, boolean allowStatic) {
        if (token == null || token.isBlank()) {
            throw new InvalidToken();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !prefix.equals(parts[0])) {
            throw new InvalidToken();
        }
        UUID presentedId;
        try {
            presentedId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidToken();
        }
        if (!expectedId.equals(presentedId)) {
            throw new InvalidToken();
        }
        String payload = parts[2];
        boolean staticQr = STATIC_WINDOW.equals(payload);
        if (staticQr && !allowStatic) {
            throw new InvalidToken();
        }
        String expectedMac = mac(secret, canonical(prefix, presentedId, payload));
        if (!MessageDigest.isEqual(expectedMac.getBytes(StandardCharsets.US_ASCII),
                parts[3].getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidToken();
        }
        if (!staticQr) {
            long presentedWindow;
            try {
                presentedWindow = Long.parseLong(payload);
            } catch (NumberFormatException exception) {
                throw new InvalidToken();
            }
            long current = windowOf(now, ttl);
            if (presentedWindow != current && presentedWindow != current - 1) {
                throw new ExpiredToken();
            }
        }
        return new Verified(staticQr);
    }

    private static String mint(String prefix, String secret, UUID id, String payload) {
        return prefix + "." + id + "." + payload + "." + mac(secret, canonical(prefix, id, payload));
    }

    private static String canonical(String prefix, UUID id, String payload) {
        return prefix + "|" + id + "|" + payload;
    }

    private static long windowOf(Instant now, Duration ttl) {
        long seconds = Math.max(1L, ttl.toSeconds());
        return Math.floorDiv(now.getEpochSecond(), seconds);
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
