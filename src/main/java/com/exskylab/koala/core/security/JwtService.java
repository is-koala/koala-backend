package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtService {

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
        return UUID.fromString(claims.getId());
    }

    public UUID extractUserId(String token) {
        String subject = extractClaim(token, Claims::getSubject);
        return UUID.fromString(subject);
    }


}
