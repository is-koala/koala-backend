package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.RealtimeCredentialService;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.security.RealtimeCredential;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RealtimeCredentialManagerTest {

    private static final String SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2VkLWluLXByb2R1Y3Rpb24tMDEyMzQ1Njc4OWFiY2RlZg==";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void issuesARealtimeCredentialForTheAuthenticatedSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId, "realtime@koala.test", Set.of(Role.ROLE_USER), sessionId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "SECRET", SECRET);
        RealtimeCredentialService credentials = new RealtimeCredentialManager(
                new SecurityManager(mock(UserDao.class)), jwtService);

        Instant requestedAt = Instant.now();
        RealtimeCredential credential = credentials.issue();

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(credential.token())
                .getPayload();
        assertEquals(Set.of("stomp"), claims.getAudience());
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(sessionId.toString(), claims.getId());
        assertTrue(credential.expiresAt().isAfter(requestedAt.plusSeconds(60)));
        assertTrue(credential.expiresAt().isBefore(requestedAt.plusSeconds(120)));
    }
}
