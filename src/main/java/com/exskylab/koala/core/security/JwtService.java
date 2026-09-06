package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;
import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private static final String STOMP_AUDIENCE = "stomp";
    private static final Set<String> STOMP_AUDIENCES = Set.of(STOMP_AUDIENCE);
    private static final Duration REALTIME_CREDENTIAL_TTL = Duration.ofSeconds(90);

    @Value("${jwt.secret}")
    private String SECRET;

    @Value("${jwt.password-set.expiration-ms}")
    private Long PASSWORD_SET_EXPIRATION_MS;

    @Value("${jwt.access-token.expiration-ms}")
    private Long ACCESS_TOKEN_EXPIRATION_MS;

    public String generatePasswordSetToken(UUID id) {
        return Jwts.builder()
                .subject(id.toString())
                .issuer("iskoala.com")
                .audience().add("password-set")
                .and().issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + PASSWORD_SET_EXPIRATION_MS))
                .signWith(getSignKey(), Jwts.SIG.HS256)
                .compact();
    }

    public UUID extractPendingRegistrationId(String token) {
       Claims claims = extractAllClaims(token);

       if (!Set.of("password-set").equals(claims.getAudience())){
              throw new IllegalArgumentException("Invalid token type");
       }
       return UUID.fromString(claims.getSubject());
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token) {
        try{
            return extractExpiration(token).before(new Date());
        } catch (Exception e) {
            return true; // If there's an error, we assume the token is expired
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }


    private Claims extractAllClaims(String token) {

        final Claims claims = Jwts
                .parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims;
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user, UUID sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime()+ ACCESS_TOKEN_EXPIRATION_MS);

        return Jwts.builder()
                .subject(user.getId().toString())
                .id(sessionId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .issuer("iskoala.com")
                .signWith(getSignKey(), Jwts.SIG.HS256)
                .compact();
    }

    public RealtimeCredential generateRealtimeCredential(UUID userId, UUID sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(REALTIME_CREDENTIAL_TTL);

        String token = Jwts.builder()
                .subject(userId.toString())
                .id(sessionId.toString())
                .audience().add(STOMP_AUDIENCE)
                .and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .issuer("iskoala.com")
                .signWith(getSignKey(), Jwts.SIG.HS256)
                .compact();

        return new RealtimeCredential(token, expiresAt);
    }

    public JwtIdentity extractHttpIdentity(String token) {
        Claims claims = extractAllClaims(token);
        requireAccessAudience(claims);
        return identityFrom(claims);
    }

    public JwtIdentity extractRealtimeIdentity(String token) {
        Claims claims = extractAllClaims(token);
        Set<String> audience = claims.getAudience();
        if (audience != null && !audience.isEmpty() && !STOMP_AUDIENCES.equals(audience)) {
            throw new UnsupportedJwtException("Token audience is not valid for realtime authentication");
        }
        return identityFrom(claims);
    }

    private JwtIdentity identityFrom(Claims claims) {
        return new JwtIdentity(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getId()));
    }

    private void requireAccessAudience(Claims claims) {
        Set<String> audience = claims.getAudience();
        if (audience != null && !audience.isEmpty()) {
            throw new UnsupportedJwtException("Token audience is not valid for HTTP authentication");
        }
    }

    public UUID extractSessionId(String token){
        String jti = extractClaim(token, Claims::getId);
        return UUID.fromString(jti);
    }

    public UUID extractSessionIdAllowingExpired(String token) {
        Claims claims;
        try {
            claims = extractAllClaims(token);
        } catch (ExpiredJwtException e) {
            claims = e.getClaims();
        }
        requireAccessAudience(claims);
        return UUID.fromString(claims.getId());
    }

    public UUID extractUserId(String token) {
        String subject = extractClaim(token, Claims::getSubject);
        return UUID.fromString(subject);
    }


}
