package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.dtos.audit.request.ClientAuditEventDto;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.AuditRetentionProperties;
import com.exskylab.koala.core.properties.RateLimitProperties;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.dataAccess.AdminAuditEntryDao;
import com.exskylab.koala.dataAccess.AdminAuditEntryDetailDao;
import com.exskylab.koala.entities.AdminAuditEntry;
import com.exskylab.koala.entities.AuditSubjectType;
import com.exskylab.koala.entities.ClientAuditAction;
import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffExportList;
import com.exskylab.koala.entities.StaffPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientAuditEventRulesTest {

    private AdminAuditEntryDao entryDao;
    private StaffAuditManager manager;

    @BeforeEach
    void setUp() {
        entryDao = mock(AdminAuditEntryDao.class);
        when(entryDao.save(any())).thenAnswer(call -> call.getArgument(0));

        RateLimitProperties rateLimits = new RateLimitProperties();
        rateLimits.setAuditEvent(new RateLimitProperties.Bucket(2, Duration.ofHours(1)));

        manager = new StaffAuditManager(entryDao, mock(AdminAuditEntryDetailDao.class),
                new AuditRetentionProperties(), new PageableSanitizer(), new RateLimiter(rateLimits), rateLimits,
                new ObjectMapper());
    }

    @Test
    void anExportIsRecordedUnderThePermissionOfTheExportedListWithTheTokensActor() {
        StaffPrincipal analyst = staff("Finans Analisti", StaffPermission.PAYMENT_READ);

        manager.recordClientEvent(analyst, export(StaffExportList.JOB_ASSIGNMENTS, false),
                "203.0.113.7", "koala-admin/1.0");

        AdminAuditEntry entry = saved().getFirst();
        assertEquals(analyst.staffId(), entry.getActorStaffId());
        assertEquals("Finans Analisti", entry.getActorDisplayName());
        assertEquals("payment.read", entry.getPermission());
        assertEquals("export", entry.getAction());
        assertEquals("job-assignments", entry.getResourceType());
        assertEquals("", entry.getResourceId());
        assertTrue(entry.isAllowed());
        assertFalse(entry.isMutating());
        assertEquals(PermissionSensitivity.FINANCIAL, entry.getSensitivity());
        assertEquals("203.0.113.7", entry.getDetail().getIp());
        assertEquals("koala-admin/1.0", entry.getDetail().getUserAgent());
        assertTrue(entry.getDetail().getRequestBody().contains("\"rowCount\":437"), entry.getDetail().getRequestBody());
        assertTrue(entry.getDetail().getRequestBody().contains("durum=CONFIRMED"), entry.getDetail().getRequestBody());
        assertNull(entry.getSubjectId());
    }

    @Test
    void aStaffMemberWithoutTheListPermissionIsRefusedAndTheRefusalIsRecorded() {
        StaffPrincipal agent = staff("Destek", StaffPermission.USER_READ);

        assertThrows(ForbiddenException.class, () -> manager.recordClientEvent(agent,
                export(StaffExportList.PAYOUTS, false), "203.0.113.7", "koala-admin/1.0"));

        AdminAuditEntry entry = saved().getFirst();
        assertFalse(entry.isAllowed());
        assertEquals("payout.reconcile", entry.getPermission());
        assertEquals("payouts", entry.getResourceType());
        assertEquals(agent.staffId(), entry.getActorStaffId());
    }

    @Test
    void theRefusalSurvivesTheForbiddenExceptionBecauseTheTransactionDoesNotRollBackForIt() throws Exception {
        Transactional transactional = StaffAuditManager.class
                .getMethod("recordClientEvent", StaffPrincipal.class, ClientAuditEventDto.class, String.class,
                        String.class)
                .getAnnotation(Transactional.class);

        assertArrayEquals(new Class<?>[]{ForbiddenException.class}, transactional.noRollbackFor());
    }

    @Test
    void personalDataInTheFileRaisesANormalListToPersonalDataButNeverLowersAFinancialOne() {
        StaffPrincipal lead = staff("Lider", StaffPermission.CONTENT_MODERATE, StaffPermission.PAYOUT_RECONCILE);

        manager.recordClientEvent(lead, export(StaffExportList.REPORTS, false), null, null);
        manager.recordClientEvent(lead, export(StaffExportList.REPORTS, true), null, null);
        manager.recordClientEvent(staff("Finans", StaffPermission.PAYOUT_RECONCILE),
                export(StaffExportList.PAYOUTS, true), null, null);

        List<AdminAuditEntry> entries = saved();
        assertEquals(PermissionSensitivity.NORMAL, entries.get(0).getSensitivity());
        assertEquals(PermissionSensitivity.PERSONAL_DATA, entries.get(1).getSensitivity());
        assertEquals(PermissionSensitivity.FINANCIAL, entries.get(2).getSensitivity());
    }

    @Test
    void anExportScopedToOnePersonCarriesThatPersonAsItsSubject() {
        UUID worker = UUID.randomUUID();
        ClientAuditEventDto event = export(StaffExportList.JOB_ASSIGNMENTS, false);
        event.setSubjectType(AuditSubjectType.USER);
        event.setSubjectId(worker);

        manager.recordClientEvent(staff("Finans", StaffPermission.PAYMENT_READ), event, null, null);

        AdminAuditEntry entry = saved().getFirst();
        assertEquals(AuditSubjectType.USER, entry.getSubjectType());
        assertEquals(worker, entry.getSubjectId());
        assertNull(entry.getRelatedSubjectId());
    }

    @Test
    void theBudgetIsPerStaffMemberAndAnOverBudgetCallLeavesNoRow() {
        StaffPrincipal first = staff("Bir", StaffPermission.DISPUTE_RESOLVE);
        StaffPrincipal second = staff("İki", StaffPermission.DISPUTE_RESOLVE);

        manager.recordClientEvent(first, export(StaffExportList.DISPUTES, false), null, null);
        manager.recordClientEvent(first, export(StaffExportList.DISPUTES, false), null, null);
        assertThrows(TooManyRequestsException.class, () -> manager.recordClientEvent(first,
                export(StaffExportList.DISPUTES, false), null, null));
        manager.recordClientEvent(second, export(StaffExportList.DISPUTES, false), null, null);

        verify(entryDao, times(3)).save(any());
    }

    @Test
    void refusedAttemptsSpendTheBudgetToo() {
        StaffPrincipal agent = staff("Destek", StaffPermission.USER_READ);

        assertThrows(ForbiddenException.class, () -> manager.recordClientEvent(agent,
                export(StaffExportList.REPORTS, false), null, null));
        assertThrows(ForbiddenException.class, () -> manager.recordClientEvent(agent,
                export(StaffExportList.REPORTS, false), null, null));
        assertThrows(TooManyRequestsException.class, () -> manager.recordClientEvent(agent,
                export(StaffExportList.REPORTS, false), null, null));

        verify(entryDao, times(2)).save(any());
    }

    @Test
    void aCallerThatIsNotARegisteredStaffMemberLeavesNoRow() {
        assertThrows(ForbiddenException.class, () -> manager.recordClientEvent(null,
                export(StaffExportList.JOB_ASSIGNMENTS, false), null, null));
        assertThrows(ForbiddenException.class, () -> manager.recordClientEvent(
                StaffPrincipal.unregistered("x@iskoala.com", Set.of()),
                export(StaffExportList.JOB_ASSIGNMENTS, false), null, null));

        verify(entryDao, never()).save(any());
    }

    @Test
    void theDisplayNameFallsBackToTheEmailLikeEveryOtherAuditRow() {
        StaffPrincipal unnamed = new StaffPrincipal(UUID.randomUUID(), "isimsiz@iskoala.com", " ", Set.of(),
                Set.of(StaffPermission.PAYMENT_READ), false);

        manager.recordClientEvent(unnamed, export(StaffExportList.JOB_ASSIGNMENTS, false), null, null);

        assertEquals("isimsiz@iskoala.com", saved().getFirst().getActorDisplayName());
    }

    private List<AdminAuditEntry> saved() {
        ArgumentCaptor<AdminAuditEntry> captor = ArgumentCaptor.forClass(AdminAuditEntry.class);
        verify(entryDao, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static StaffPrincipal staff(String name, StaffPermission... permissions) {
        return new StaffPrincipal(UUID.randomUUID(), name.toLowerCase(java.util.Locale.ROOT) + "@iskoala.com", name,
                Set.of(), Set.of(permissions), false);
    }

    private static ClientAuditEventDto export(StaffExportList list, boolean piiIncluded) {
        ClientAuditEventDto event = new ClientAuditEventDto();
        event.setAction(ClientAuditAction.EXPORT);
        event.setResourceType(list);
        event.setFilters("durum=CONFIRMED&from=2026-09-01T00:00:00Z");
        event.setRowCount(437);
        event.setPiiIncluded(piiIncluded);
        return event;
    }
}
