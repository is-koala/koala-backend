package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.LoginAttemptService;
import com.exskylab.koala.core.properties.LoginAttemptRetentionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LoginAttemptRetentionSchedulerTest {

    private LoginAttemptService loginAttemptService;
    private LoginAttemptRetentionProperties properties;
    private LoginAttemptRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        loginAttemptService = mock(LoginAttemptService.class);
        properties = new LoginAttemptRetentionProperties();
        scheduler = new LoginAttemptRetentionScheduler(loginAttemptService, properties);
    }

    @Test
    void bothWindowsAreEnforcedOnEveryRun() {
        scheduler.enforceRetention();

        verify(loginAttemptService).purgeExpiredUnattributed();
        verify(loginAttemptService).purgeExpiredAttributed();
        verifyNoMoreInteractions(loginAttemptService);
    }

    @Test
    void nothingIsRemovedWhileTheJobIsSwitchedOff() {
        properties.setEnabled(false);

        scheduler.enforceRetention();

        verify(loginAttemptService, never()).purgeExpiredUnattributed();
        verify(loginAttemptService, never()).purgeExpiredAttributed();
    }

    @Test
    void aFailedRunNeverEscapesTheScheduler() {
        when(loginAttemptService.purgeExpiredUnattributed())
                .thenThrow(new IllegalStateException("veritabani yok"));

        scheduler.enforceRetention();

        verify(loginAttemptService).purgeExpiredUnattributed();
    }

    @Test
    void theOwnerlessWindowIsMuchShorterThanTheAttributedOne() {
        assertEquals(30, properties.getUnattributedDays());
        assertEquals(365, properties.getAttributedDays());
        assertTrue(properties.getUnattributedDays() < properties.getAttributedDays(),
                "Hiçbir hesaba ait olmayan adres, hesabın kendi geçmişinden uzun yaşayamaz.");
    }

    @Test
    void theJobIsOnUnlessSomebodyTurnsItOff() {
        assertTrue(properties.isEnabled());
    }
}
