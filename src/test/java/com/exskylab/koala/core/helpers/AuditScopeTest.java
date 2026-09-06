package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffPermission;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditScopeTest {

    private static final StaffPermission[] NONE = {};

    @Test
    void everyChangeIsAudited() {
        assertTrue(AuditScope.isAuditable(true, true, PermissionSensitivity.NORMAL, false, false));
    }

    @Test
    void aDeniedRequestIsAuditedEvenWhenItWouldNotBeWorthReading() {
        assertTrue(AuditScope.isAuditable(false, false, PermissionSensitivity.NORMAL, false, false),
                "Reddedilen istek yetki yükseltme denemesinin tek izidir.");
    }

    @Test
    void aPlainListLoadIsNotAudited() {
        assertFalse(AuditScope.isAuditable(true, false, PermissionSensitivity.NORMAL, false, false));
    }

    @Test
    void aFilteredSearchIsAudited() {
        assertTrue(AuditScope.isAuditable(true, false, PermissionSensitivity.NORMAL, false, true),
                "Sorgunun kendisi kişisel veri.");
    }

    @Test
    void readingASingleRecordIsAudited() {
        assertTrue(AuditScope.isAuditable(true, false, PermissionSensitivity.NORMAL, true, false));
    }

    @Test
    void aSensitiveReadIsAuditedEvenWithoutAFilter() {
        assertTrue(AuditScope.isAuditable(true, false, PermissionSensitivity.PERSONAL_DATA, false, false));
        assertTrue(AuditScope.isAuditable(true, false, PermissionSensitivity.FINANCIAL, false, false));
    }

    @Test
    void pagingParametersAreNotFilters() {
        assertFalse(AuditScope.hasFilter(Map.of("page", new String[]{"2"}, "size", new String[]{"20"},
                "sort", new String[]{"occurredAt,desc"})));
    }

    @Test
    void aBlankParameterIsNotAFilter() {
        assertFalse(AuditScope.hasFilter(Map.of("query", new String[]{"  "})));
    }

    @Test
    void anyOtherParameterIsAFilter() {
        assertTrue(AuditScope.hasFilter(Map.of("page", new String[]{"0"}, "query", new String[]{"a@b.com"})));
    }

    @Test
    void aGetNeverCountsAsMutatingEvenUnderAMutatingPermission() {
        assertFalse(AuditScope.isMutating("GET", new StaffPermission[]{StaffPermission.COMPANY_APPROVE}),
                "GET /admin/companies onay iznini taşıyor ama hiçbir şeyi değiştirmiyor.");
        assertTrue(AuditScope.isMutating("POST", new StaffPermission[]{StaffPermission.COMPANY_APPROVE}));
    }

    @Test
    void sensitivityIsTheStrongestOfTheDeclaredPermissions() {
        assertEquals(PermissionSensitivity.NORMAL, AuditScope.sensitivityOf(NONE));
        assertEquals(PermissionSensitivity.FINANCIAL, AuditScope.sensitivityOf(
                new StaffPermission[]{StaffPermission.USER_READ, StaffPermission.PAYOUT_RELEASE}));
        assertEquals(PermissionSensitivity.PERSONAL_DATA, AuditScope.sensitivityOf(
                new StaffPermission[]{StaffPermission.USER_READ, StaffPermission.USER_READ_PII}));
    }

    @Test
    void theResourceTypeIsTheCollectionUnderTheAdminPrefix() {
        assertEquals("users", AuditScope.resourceTypeOf("/admin/users/{userId}/roles"));
        assertEquals("audit-log", AuditScope.resourceTypeOf("/admin/audit-log"));
        assertEquals("job-assignments", AuditScope.resourceTypeOf("/admin/job-assignments/{id}/release"));
        assertEquals("admin", AuditScope.resourceTypeOf("/admin"));
    }

    @Test
    void theRecordIdIsTheFirstVariableThePatternDeclares() {
        assertEquals("u-1", AuditScope.resourceIdOf("/admin/users/{userId}/roles/{role}",
                Map.of("userId", "u-1", "role", "ROLE_ADMIN")));
        assertEquals("cancellation.late-fee-rate", AuditScope.resourceIdOf("/admin/settings/{key}",
                Map.of("key", "cancellation.late-fee-rate")));
    }

    @Test
    void aListRequestTargetsNoRecordAndSaysSoWithoutNull() {
        assertEquals("", AuditScope.resourceIdOf("/admin/users", Map.of()),
                "Çekirdek alanların hiçbiri null olmamalı.");
    }
}
