package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import com.exskylab.koala.dataAccess.IdentityChallengeDao;
import com.exskylab.koala.entities.IdentityChallenge;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityChallengeRulesTest {

    private IdentityChallengeDao dao;
    private IdentityVerificationProperties properties;
    private IdentityChallengeManager manager;
    private User user;

    @BeforeEach
    void setUp() {
        dao = mock(IdentityChallengeDao.class);
        properties = new IdentityVerificationProperties();
        manager = new IdentityChallengeManager(dao, properties);

        user = new User();
        user.setId(UUID.randomUUID());

        when(dao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private IdentityChallenge stored(String challenge, Instant expiresAt) {
        IdentityChallenge record = new IdentityChallenge();
        record.setUser(user);
        record.setChallenge(challenge);
        record.setExpiresAt(expiresAt);
        when(dao.findByUserIdAndChallengeAndConsumedAtIsNull(user.getId(), challenge))
                .thenReturn(Optional.of(record));
        return record;
    }

    @Test
    void anIssuedChallengeIsEightRandomBytesAndIsStored() {
        String challenge = manager.issue(user);

        assertEquals(8, Base64.getDecoder().decode(challenge).length);
        verify(dao).save(any(IdentityChallenge.class));
    }

    @Test
    void twoChallengesAreNeverTheSame() {
        assertNotEquals(manager.issue(user), manager.issue(user));
    }

    @Test
    void anUnknownChallengeIsRefused() {
        when(dao.findByUserIdAndChallengeAndConsumedAtIsNull(any(), any())).thenReturn(Optional.empty());

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.consume(user, "AAAAAAAAAAA=", new byte[]{1}, null, new byte[]{2}));

        assertEquals("IDENTITY_CHALLENGE_INVALID", thrown.getErrorCode());
    }

    @Test
    void anExpiredChallengeIsRefused() {
        stored("AAAAAAAAAAA=", Instant.now().minus(1, ChronoUnit.MINUTES));

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.consume(user, "AAAAAAAAAAA=", new byte[]{1}, null, new byte[]{2}));

        assertEquals("IDENTITY_CHALLENGE_INVALID", thrown.getErrorCode());
    }

    @Test
    void aChallengeThatFailsTheChipSignatureIsNotMarkedConsumed() {
        IdentityChallenge record = stored("AAAAAAAAAAA=", Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThrows(ValidationException.class,
                () -> manager.consume(user, "AAAAAAAAAAA=", "anahtar degil".getBytes(), null, new byte[64]));

        assertNull(record.getConsumedAt());
    }

    @Test
    void aChallengeBelongingToAnotherUserIsNotFound() {
        stored("AAAAAAAAAAA=", Instant.now().plus(5, ChronoUnit.MINUTES));

        User someoneElse = new User();
        someoneElse.setId(UUID.randomUUID());

        assertThrows(ValidationException.class,
                () -> manager.consume(someoneElse, "AAAAAAAAAAA=", new byte[]{1}, null, new byte[]{2}));
    }

    @Test
    void aFailedChipSignatureLeavesOneDiagnosticLineWithoutTheChallengeOrTheSignature() throws Exception {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(IdentityChallengeManager.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            stored("AAAAAAAAAAA=", Instant.now().plus(5, ChronoUnit.MINUTES));
            byte[] signature = new byte[64];
            java.util.Arrays.fill(signature, (byte) 0x42);

            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> manager.consume(user, "AAAAAAAAAAA=", ellipticCurveChipKey(), null, signature));

            assertEquals("IDENTITY_CHIP_NOT_PRESENT", thrown.getErrorCode());

            String line = appender.list.stream()
                    .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("Active authentication failed"))
                    .findFirst()
                    .orElseThrow();

            assertTrue(line.contains("key=EC"), line);
            assertTrue(line.contains("curve=prime256v1"), line);
            assertTrue(line.contains("bits=256"), line);
            assertTrue(line.contains("signatureBytes=64"), line);
            assertTrue(line.contains("dg14=absent"), line);
            assertTrue(line.contains("attempted=10"), line);
            assertFalse(line.contains("AAAAAAAAAAA="), line);
            assertFalse(line.contains("42424242"), line);
            assertFalse(line.contains("QkJCQkJC"), line);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static byte[] ellipticCurveChipKey() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        byte[] key = generator.generateKeyPair().getPublic().getEncoded();

        byte[] wrapped = new byte[key.length + 2];
        wrapped[0] = 0x6F;
        wrapped[1] = (byte) key.length;
        System.arraycopy(key, 0, wrapped, 2, key.length);
        return wrapped;
    }
}
