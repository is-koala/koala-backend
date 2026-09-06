package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.properties.AuditRetentionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AdminAuditRetentionSchedulerTest {

    private StaffAuditService staffAuditService;
    private AuditRetentionProperties properties;
    private AdminAuditRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        staffAuditService = mock(StaffAuditService.class);
        properties = new AuditRetentionProperties();
        scheduler = new AdminAuditRetentionScheduler(staffAuditService, properties);
    }

    @Test
    void bothWindowsAreEnforcedOnEveryRun() {
        scheduler.enforceRetention();

        verify(staffAuditService).purgeExpiredDetails();
        verify(staffAuditService).purgeExpiredEntries();
        verifyNoMoreInteractions(staffAuditService);
    }

    @Test
    void nothingIsRemovedWhileTheJobIsSwitchedOff() {
        properties.setEnabled(false);

        scheduler.enforceRetention();

        verify(staffAuditService, never()).purgeExpiredDetails();
        verify(staffAuditService, never()).purgeExpiredEntries();
    }

    @Test
    void aFailedRunNeverEscapesTheScheduler() {
        when(staffAuditService.purgeExpiredDetails()).thenThrow(new IllegalStateException("veritabani yok"));

        scheduler.enforceRetention();

        verify(staffAuditService).purgeExpiredDetails();
    }

    @Test
    void theFinancialWindowIsFiveTimesTheOrdinaryOne() {
        org.junit.jupiter.api.Assertions.assertEquals(24, properties.getCoreMonths());
        org.junit.jupiter.api.Assertions.assertEquals(120, properties.getFinancialCoreMonths());
        org.junit.jupiter.api.Assertions.assertEquals(6, properties.getDetailMonths());
    }
}
