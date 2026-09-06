package com.exskylab.koala.core.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpiredTokenRefreshTest {

    private static final String SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2VkLWluLXByb2R1Y3Rpb24tMDEyMzQ1Njc4OWFiY2RlZg==";

    private JwtService jwtService;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "SECRET", SECRET);
        ReflectionTestUtils.setField(jwtService, "ACCESS_TOKEN_EXPIRATION_MS", 43_200_000L);
        ReflectionTestUtils.setField(jwtService, "PASSWORD_SET_EXPIRATION_MS", 600_000L);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String tokenExpiredAgo(long millisAgo) {
        Date issued = new Date(System.currentTimeMillis() - millisAgo - 1000);
        Date expiry = new Date(System.currentTimeMillis() - millisAgo);
        return Jwts.builder()
                .subject(userId.toString())
                .id(sessionId.toString())
                .issuedAt(issued)
                .expiration(expiry)
                .issuer("iskoala.com")
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void aLiveTokenStillYieldsItsSessionId() {
        String live = Jwts.builder()
                .subject(userId.toString())
                .id(sessionId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();

        assertEquals(sessionId, jwtService.extractSessionIdAllowingExpired(live));
    }

    @Test
    void anExpiredTokenStillYieldsItsSessionIdSoRefreshCanWork() {
        String expired = tokenExpiredAgo(60_000);

        assertEquals(sessionId, jwtService.extractSessionIdAllowingExpired(expired),
                "Yenileme ucu tam da token suresi doldugu icin cagriliyor; ayristirma patlamamali.");
    }

    @Test
    void aTokenExpiredForDaysStillWorks() {
        String longExpired = tokenExpiredAgo(30L * 24 * 60 * 60 * 1000);

        assertEquals(sessionId, jwtService.extractSessionIdAllowingExpired(longExpired));
    }

    @Test
    void theStrictExtractorStillRejectsAnExpiredToken() {
        String expired = tokenExpiredAgo(60_000);

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractSessionId(expired),
                "Normal yolda suresi dolmus token kabul edilmemeli.");
    }

    @Test
    void aForgedSignatureIsRejectedEvenWhenExpiryIsTolerated() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtdGVzdGluZ3g="));

        String forged = Jwts.builder()
                .subject(userId.toString())
                .id(sessionId.toString())
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThrows(SignatureException.class,
                () -> jwtService.extractSessionIdAllowingExpired(forged),
                "Sure toleransi imza dogrulamasini gevsetmemeli.");
    }

    @Test
    void garbageIsStillRejected() {
        assertThrows(RuntimeException.class,
                () -> jwtService.extractSessionIdAllowingExpired("bu.bir.token.degil"));
    }

    @Test
    void aRealtimeCredentialCannotEnterTheRefreshFlow() {
        String realtimeCredential = Jwts.builder()
                .subject(userId.toString())
                .id(sessionId.toString())
                .audience().add("stomp")
                .and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();

        assertThrows(UnsupportedJwtException.class,
                () -> jwtService.extractSessionIdAllowingExpired(realtimeCredential));
    }
}
