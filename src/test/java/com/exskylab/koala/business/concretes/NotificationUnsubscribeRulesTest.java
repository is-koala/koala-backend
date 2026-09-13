package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuthLinkService;
import com.exskylab.koala.core.constants.NotificationPreferenceMessages;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.security.PasswordResetTokenGenerator;
import com.exskylab.koala.core.security.TokenDigest;
import com.exskylab.koala.dataAccess.UnsubscribeTokenDao;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.UnsubscribeToken;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationUnsubscribeRulesTest {

    private static final String RAW_TOKEN = "8Wm7QpVw2Zt5Yb1Nc9Ke3Lf6Rh0Sj4Ud8Xa2Bg5Cq0";
    private static final long NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000;

    private UnsubscribeTokenDao tokenDao;
    private UnsubscribeTokenManager manager;
    private User user;

    @BeforeEach
    void setUp() {
        tokenDao = mock(UnsubscribeTokenDao.class);
        when(tokenDao.save(any())).thenAnswer(call -> call.getArgument(0));
        when(tokenDao.findByTokenDigest(any())).thenReturn(Optional.empty());

        PasswordResetTokenGenerator generator = mock(PasswordResetTokenGenerator.class);
        when(generator.generate()).thenReturn(RAW_TOKEN);

        AuthLinkService authLinkService = mock(AuthLinkService.class);
        when(authLinkService.unsubscribeLandingLink(any()))
                .thenAnswer(call -> "https://iskoala.com/bildirim-tercihleri/cikis?token=" + call.getArgument(0));

        user = new User();
        user.setId(UUID.randomUUID());

        manager = new UnsubscribeTokenManager(tokenDao, generator, authLinkService, NINETY_DAYS_MS);
    }

    private UnsubscribeToken savedToken() {
        ArgumentCaptor<UnsubscribeToken> captor = ArgumentCaptor.forClass(UnsubscribeToken.class);
        org.mockito.Mockito.verify(tokenDao).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void theLinkCarriesTheTokenAndTheStoredRowCarriesOnlyItsDigest() {
        String link = manager.issueLink(user, NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL);

        assertTrue(link.endsWith("token=" + RAW_TOKEN), link);
        assertFalse(savedToken().getTokenDigest().contains(RAW_TOKEN),
                "ham token veritabanına yazılmamalı");
        assertEquals(64, savedToken().getTokenDigest().length());
    }

    @Test
    void theStoredRowRemembersExactlyWhichChannelTheLinkSwitchesOff() {
        manager.issueLink(user, NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL);

        assertEquals(user, savedToken().getUser());
        assertEquals(NotificationCategory.JOB_ALERTS, savedToken().getCategory());
        assertEquals(NotificationChannel.EMAIL, savedToken().getChannel());
    }

    @Test
    void anUnknownTokenIsRefusedWithTheInvalidCode() {
        ValidationException refusal = assertThrows(ValidationException.class,
                () -> manager.consume("baska-bir-token"));

        assertEquals(NotificationPreferenceMessages.UNSUBSCRIBE_TOKEN_INVALID, refusal.getMessage());
        assertEquals("NOTIFICATION_UNSUBSCRIBE_TOKEN_INVALID", refusal.getErrorCode());
    }

    @Test
    void aTamperedTokenIsIndistinguishableFromAnUnknownOne() {
        UnsubscribeToken live = tokenFor(Instant.now().plusSeconds(600));
        when(tokenDao.findByTokenDigest(TokenDigest.of(RAW_TOKEN))).thenReturn(Optional.of(live));

        ValidationException tampered = assertThrows(ValidationException.class,
                () -> manager.consume(RAW_TOKEN.substring(1) + "x"));
        ValidationException unknown = assertThrows(ValidationException.class,
                () -> manager.consume("baska-bir-token"));

        assertEquals(unknown.getMessage(), tampered.getMessage());
        assertEquals(unknown.getErrorCode(), tampered.getErrorCode());
        assertEquals(unknown.getHttpStatus(), tampered.getHttpStatus());
    }

    @Test
    void anExpiredLinkIsRefusedWithItsOwnCodeAndStatusStaysTheSame() {
        UnsubscribeToken expired = tokenFor(Instant.now().minusSeconds(1));
        when(tokenDao.findByTokenDigest(any())).thenReturn(Optional.of(expired));

        ValidationException refusal = assertThrows(ValidationException.class, () -> manager.consume(RAW_TOKEN));
        ValidationException unknown = assertThrows(ValidationException.class,
                () -> manager.consume("baska-bir-token"));

        assertEquals(NotificationPreferenceMessages.UNSUBSCRIBE_TOKEN_EXPIRED, refusal.getMessage());
        assertEquals("NOTIFICATION_UNSUBSCRIBE_TOKEN_EXPIRED", refusal.getErrorCode());
        assertEquals(unknown.getHttpStatus(), refusal.getHttpStatus());
    }

    @Test
    void anExpiredLinkIsNotStampedAsUsed() {
        UnsubscribeToken expired = tokenFor(Instant.now().minusSeconds(1));
        when(tokenDao.findByTokenDigest(any())).thenReturn(Optional.of(expired));

        assertThrows(ValidationException.class, () -> manager.consume(RAW_TOKEN));

        assertEquals(null, expired.getLastUsedAt());
        verify(tokenDao, never()).save(any());
    }

    @Test
    void neitherRefusalEchoesTheRawToken() {
        UnsubscribeToken expired = tokenFor(Instant.now().minusSeconds(1));
        when(tokenDao.findByTokenDigest(TokenDigest.of(RAW_TOKEN))).thenReturn(Optional.of(expired));

        ValidationException expiredRefusal = assertThrows(ValidationException.class,
                () -> manager.consume(RAW_TOKEN));
        ValidationException unknownRefusal = assertThrows(ValidationException.class,
                () -> manager.consume("baska-bir-token"));

        assertFalse(expiredRefusal.getMessage().contains(RAW_TOKEN), expiredRefusal.getMessage());
        assertFalse(unknownRefusal.getMessage().contains("baska-bir-token"), unknownRefusal.getMessage());
    }

    @Test
    void aLiveLinkResolvesToItsTargetAndIsStampedAsUsed() {
        UnsubscribeToken live = tokenFor(Instant.now().plusSeconds(600));
        when(tokenDao.findByTokenDigest(any())).thenReturn(Optional.of(live));
        Instant before = Instant.now();

        assertEquals(live, manager.consume(RAW_TOKEN));

        assertNotNull(live.getLastUsedAt(), "canli baglanti kullanildi diye damgalanmali");
        assertFalse(live.getLastUsedAt().isBefore(before));
    }

    @Test
    void clickingTheSameLinkTwiceStillWorksBecauseSwitchingOffIsIdempotent() {
        UnsubscribeToken live = tokenFor(Instant.now().plusSeconds(600));
        when(tokenDao.findByTokenDigest(any())).thenReturn(Optional.of(live));

        manager.consume(RAW_TOKEN);

        assertEquals(live, manager.consume(RAW_TOKEN));
    }

    private UnsubscribeToken tokenFor(Instant expiresAt) {
        UnsubscribeToken token = new UnsubscribeToken();
        token.setUser(user);
        token.setCategory(NotificationCategory.JOB_ALERTS);
        token.setChannel(NotificationChannel.EMAIL);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
