package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuditSubjectService;
import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.dtos.audit.request.ClientAuditEventDto;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.RateLimitProperties;
import com.exskylab.koala.core.security.AuditSubject;
import com.exskylab.koala.core.security.StaffAuditRecord;
import com.exskylab.koala.core.security.StaffPrincipal;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientAuditEventRulesTest {

    private StaffAuditService staffAuditService;
    private AuditSubjectService auditSubjectService;
    private ClientAuditEventManager manager;

    @BeforeEach
    void setUp() {
        staffAuditService = mock(StaffAuditService.class);
        auditSubjectService = mock(AuditSubjectService.class);
        when(auditSubjectService.exists(any())).thenReturn(true);
        when(staffAuditService.recordClientEvent(any(), any(), anyInt(), anyBoolean()))
                .thenAnswer(call -> new AdminAuditEntry());

        RateLimitProperties rateLimits = new RateLimitProperties();
        rateLimits.setAuditEvent(new RateLimitProperties.Bucket(2, Duration.ofHours(1)));

        manager = new ClientAuditEventManager(staffAuditService, auditSubjectService, new RateLimiter(rateLimits),
                rateLimits, new ObjectMapper());
    }

    @Test
    void anExportIsRecordedUnderThePermissionOfTheExportedListWithTheTokensActor() {
        StaffPrincipal analyst = staff("Finans Analisti", StaffPermission.PAYMENT_READ);

        manager.record(analyst, export(StaffExportList.JOB_ASSIGNMENTS, false), "203.0.113.7", "koala-admin/1.0");

        StaffAuditRecord record = recorded().getFirst();
        assertEquals(analyst.staffId(), record.actorStaffId());
        assertEquals("Finans Analisti", record.actorDisplayName());
        assertEquals("payment.read", record.permission());
        assertEquals("export", record.action());
        assertEquals("job-assignments", record.resourceType());
        assertEquals("", record.resourceId());
        assertTrue(record.allowed());
        assertFalse(record.mutating());
        assertEquals(PermissionSensitivity.FINANCIAL, record.sensitivity());
        assertEquals("203.0.113.7", record.ip());
        assertEquals("koala-admin/1.0", record.userAgent());
        assertNull(record.subjects().subject());

        String body = recordedBodies().getFirst();
        assertTrue(body.contains("\"rowCount\":437") && body.contains("durum=CONFIRMED"), body);
        verify(staffAuditService).recordClientEvent(any(), anyString(), org.mockito.ArgumentMatchers.eq(437),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void aStaffMemberWithoutTheListPermissionIsRefusedAndTheRefusalIsRecorded() {
        StaffPrincipal agent = staff("Destek", StaffPermission.USER_READ);

        ForbiddenException refused = assertThrows(ForbiddenException.class, () -> manager.record(agent,
                export(StaffExportList.PAYOUTS, false), "203.0.113.7", "koala-admin/1.0"));

        assertEquals("AUDIT_EVENT_LIST_PERMISSION_MISSING", refused.getErrorCode());
        StaffAuditRecord record = recorded().getFirst();
        assertFalse(record.allowed());
        assertEquals("payout.reconcile", record.permission());
        assertEquals("payouts", record.resourceType());
        assertEquals(agent.staffId(), record.actorStaffId());
    }

    @Test
    void theRefusalSurvivesTheForbiddenExceptionBecauseTheTransactionDoesNotRollBackForIt() throws Exception {
        Transactional transactional = ClientAuditEventManager.class
                .getMethod("record", StaffPrincipal.class, ClientAuditEventDto.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertArrayEquals(new Class<?>[]{ForbiddenException.class}, transactional.noRollbackFor());
    }

    @Test
    void personalDataInTheFileRaisesANormalListToPersonalDataButNeverLowersAFinancialOne() {
        StaffPrincipal lead = staff("Lider", StaffPermission.CONTENT_MODERATE, StaffPermission.PAYOUT_RECONCILE);

        manager.record(lead, export(StaffExportList.REPORTS, false), null, null);
        manager.record(lead, export(StaffExportList.REPORTS, true), null, null);
        manager.record(staff("Finans", StaffPermission.PAYOUT_RECONCILE), export(StaffExportList.PAYOUTS, true),
                null, null);

        List<StaffAuditRecord> records = recorded();
        assertEquals(PermissionSensitivity.NORMAL, records.get(0).sensitivity());
        assertEquals(PermissionSensitivity.PERSONAL_DATA, records.get(1).sensitivity());
        assertEquals(PermissionSensitivity.FINANCIAL, records.get(2).sensitivity());
    }

    @Test
    void anExportFilteredByOnePersonCarriesThatPersonAsItsSubject() {
        UUID worker = UUID.randomUUID();
        ClientAuditEventDto event = export(StaffExportList.JOB_ASSIGNMENTS, false);
        event.setSubjectType(AuditSubjectType.USER);
        event.setSubjectId(worker);

        manager.record(staff("Finans", StaffPermission.PAYMENT_READ), event, null, null);

        StaffAuditRecord record = recorded().getFirst();
        assertEquals(AuditSubject.user(worker), record.subjects().subject());
        assertNull(record.subjects().related());
    }

    @Test
    void aWorkerAndEmployerFilteredExportNamesTheEmployerAsTheCounterparty() {
        UUID worker = UUID.randomUUID();
        UUID employer = UUID.randomUUID();
        ClientAuditEventDto event = export(StaffExportList.JOB_ASSIGNMENTS, false);
        event.setSubjectType(AuditSubjectType.USER);
        event.setSubjectId(worker);
        event.setRelatedSubjectType(AuditSubjectType.USER);
        event.setRelatedSubjectId(employer);

        manager.record(staff("Finans", StaffPermission.PAYMENT_READ), event, null, null);

        StaffAuditRecord record = recorded().getFirst();
        assertEquals(AuditSubject.user(worker), record.subjects().subject());
        assertEquals(AuditSubject.user(employer), record.subjects().related());
    }

    @Test
    void aSubjectThatDoesNotExistIsRefusedBeforeAnythingIsRecorded() {
        UUID stranger = UUID.randomUUID();
        when(auditSubjectService.exists(AuditSubject.user(stranger))).thenReturn(false);
        ClientAuditEventDto event = export(StaffExportList.JOB_ASSIGNMENTS, false);
        event.setSubjectType(AuditSubjectType.USER);
        event.setSubjectId(stranger);

        ValidationException refused = assertThrows(ValidationException.class,
                () -> manager.record(staff("Finans", StaffPermission.PAYMENT_READ), event, null, null));

        assertEquals("AUDIT_EVENT_SUBJECT_NOT_FOUND", refused.getErrorCode());
        verify(staffAuditService, never()).recordClientEvent(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void aCounterpartyThatDoesNotExistIsRefusedToo() {
        UUID worker = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        when(auditSubjectService.exists(AuditSubject.user(ghost))).thenReturn(false);
        ClientAuditEventDto event = export(StaffExportList.JOB_ASSIGNMENTS, false);
        event.setSubjectType(AuditSubjectType.USER);
        event.setSubjectId(worker);
        event.setRelatedSubjectType(AuditSubjectType.USER);
        event.setRelatedSubjectId(ghost);

        assertThrows(ValidationException.class,
                () -> manager.record(staff("Finans", StaffPermission.PAYMENT_READ), event, null, null));

        verify(staffAuditService, never()).recordClientEvent(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void aStaffMemberWithoutTheListPermissionGetsTheSameRefusalAndRowWhetherOrNotTheSubjectExists() {
        StaffPrincipal agent = staff("Destek", StaffPermission.USER_READ);
        UUID stranger = UUID.randomUUID();
        UUID worker = UUID.randomUUID();
        when(auditSubjectService.exists(AuditSubject.user(stranger))).thenReturn(false);
        ClientAuditEventDto aboutNobody = export(StaffExportList.JOB_ASSIGNMENTS, false);
        aboutNobody.setSubjectType(AuditSubjectType.USER);
        aboutNobody.setSubjectId(stranger);
        ClientAuditEventDto aboutSomeone = export(StaffExportList.JOB_ASSIGNMENTS, false);
        aboutSomeone.setSubjectType(AuditSubjectType.USER);
        aboutSomeone.setSubjectId(worker);

        ForbiddenException ghost = assertThrows(ForbiddenException.class,
                () -> manager.record(agent, aboutNobody, null, null));
        ForbiddenException real = assertThrows(ForbiddenException.class,
                () -> manager.record(agent, aboutSomeone, null, null));

        assertEquals("AUDIT_EVENT_LIST_PERMISSION_MISSING", ghost.getErrorCode());
        assertEquals("AUDIT_EVENT_LIST_PERMISSION_MISSING", real.getErrorCode());
        List<StaffAuditRecord> rows = recorded();
        assertEquals(2, rows.size());
        assertFalse(rows.get(0).allowed());
        assertNull(rows.get(0).subjects().subject(), "Var olmayan kişi ret satırına konu olarak yazılmaz.");
        assertFalse(rows.get(1).allowed());
        assertEquals(AuditSubject.user(worker), rows.get(1).subjects().subject());
    }

    @Test
    void anExportWithoutASubjectNeverAsksWhetherAnyoneExists() {
        manager.record(staff("Finans", StaffPermission.PAYMENT_READ), export(StaffExportList.JOB_ASSIGNMENTS, false),
                null, null);

        verify(auditSubjectService, never()).exists(any());
    }

    @Test
    void theBudgetIsPerStaffMemberAndAnOverBudgetCallLeavesNoRow() {
        StaffPrincipal first = staff("Bir", StaffPermission.DISPUTE_RESOLVE);
        StaffPrincipal second = staff("İki", StaffPermission.DISPUTE_RESOLVE);

        manager.record(first, export(StaffExportList.DISPUTES, false), null, null);
        manager.record(first, export(StaffExportList.DISPUTES, false), null, null);
        TooManyRequestsException tooMany = assertThrows(TooManyRequestsException.class,
                () -> manager.record(first, export(StaffExportList.DISPUTES, false), null, null));
        manager.record(second, export(StaffExportList.DISPUTES, false), null, null);

        assertEquals("AUDIT_EVENT_TOO_MANY", tooMany.getErrorCode());
        verify(staffAuditService, times(3)).recordClientEvent(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void refusedAttemptsSpendTheBudgetToo() {
        StaffPrincipal agent = staff("Destek", StaffPermission.USER_READ);

        assertThrows(ForbiddenException.class,
                () -> manager.record(agent, export(StaffExportList.REPORTS, false), null, null));
        assertThrows(ForbiddenException.class,
                () -> manager.record(agent, export(StaffExportList.REPORTS, false), null, null));
        assertThrows(TooManyRequestsException.class,
                () -> manager.record(agent, export(StaffExportList.REPORTS, false), null, null));

        verify(staffAuditService, times(2)).recordClientEvent(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void aCallerThatIsNotARegisteredStaffMemberIsRefusedWithItsOwnCodeAndLeavesNoRow() {
        ForbiddenException bridge = assertThrows(ForbiddenException.class,
                () -> manager.record(null, export(StaffExportList.JOB_ASSIGNMENTS, false), null, null));
        ForbiddenException unregistered = assertThrows(ForbiddenException.class, () -> manager.record(
                StaffPrincipal.unregistered("x@iskoala.com", Set.of()),
                export(StaffExportList.JOB_ASSIGNMENTS, false), null, null));

        assertEquals("AUDIT_EVENT_STAFF_ONLY", bridge.getErrorCode());
        assertEquals("AUDIT_EVENT_STAFF_ONLY", unregistered.getErrorCode());
        verify(staffAuditService, never()).recordClientEvent(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void theDisplayNameFallsBackToTheEmailLikeEveryOtherAuditRow() {
        StaffPrincipal unnamed = new StaffPrincipal(UUID.randomUUID(), "isimsiz@iskoala.com", " ", Set.of(),
                Set.of(StaffPermission.PAYMENT_READ), false);

        manager.record(unnamed, export(StaffExportList.JOB_ASSIGNMENTS, false), null, null);

        assertEquals("isimsiz@iskoala.com", recorded().getFirst().actorDisplayName());
    }

    private List<StaffAuditRecord> recorded() {
        ArgumentCaptor<StaffAuditRecord> captor = ArgumentCaptor.forClass(StaffAuditRecord.class);
        verify(staffAuditService, atLeastOnce()).recordClientEvent(captor.capture(), any(), anyInt(), anyBoolean());
        return captor.getAllValues();
    }

    private List<String> recordedBodies() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(staffAuditService, atLeastOnce()).recordClientEvent(any(), captor.capture(), anyInt(), anyBoolean());
        return captor.getAllValues();
    }

    private static StaffPrincipal staff(String name, StaffPermission... permissions) {
        return new StaffPrincipal(UUID.randomUUID(), name.toLowerCase(Locale.ROOT) + "@iskoala.com", name,
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
