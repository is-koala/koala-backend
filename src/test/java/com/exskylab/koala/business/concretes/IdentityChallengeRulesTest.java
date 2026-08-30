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
                () -> manager.consume(user, "AAAAAAAAAAA=", new byte[]{1}, new byte[]{2}));

        assertEquals("IDENTITY_CHALLENGE_INVALID", thrown.getErrorCode());
    }

    @Test
    void anExpiredChallengeIsRefused() {
        stored("AAAAAAAAAAA=", Instant.now().minus(1, ChronoUnit.MINUTES));

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.consume(user, "AAAAAAAAAAA=", new byte[]{1}, new byte[]{2}));

        assertEquals("IDENTITY_CHALLENGE_INVALID", thrown.getErrorCode());
    }

    @Test
    void aChallengeThatFailsTheChipSignatureIsNotMarkedConsumed() {
        IdentityChallenge record = stored("AAAAAAAAAAA=", Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThrows(ValidationException.class,
                () -> manager.consume(user, "AAAAAAAAAAA=", "anahtar degil".getBytes(), new byte[64]));

        assertNull(record.getConsumedAt());
    }

    @Test
    void aChallengeBelongingToAnotherUserIsNotFound() {
        stored("AAAAAAAAAAA=", Instant.now().plus(5, ChronoUnit.MINUTES));

        User someoneElse = new User();
        someoneElse.setId(UUID.randomUUID());

        assertThrows(ValidationException.class,
                () -> manager.consume(someoneElse, "AAAAAAAAAAA=", new byte[]{1}, new byte[]{2}));
    }
}
