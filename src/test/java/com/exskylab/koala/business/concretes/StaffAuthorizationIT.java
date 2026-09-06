package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAuthorizationService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.security.StaffAuthorization;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private StaffAuthorizationService staffAuthorizationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreTheSeededGraph() {
        jdbcTemplate.update("delete from staff_role_permissions where staff_role_id in "
                + "(select id from staff_roles where code like 'TEST_%')");
        jdbcTemplate.update("delete from staff_role_includes where parent_role_id in "
                + "(select id from staff_roles where code like 'TEST_%') "
                + "or child_role_id in (select id from staff_roles where code like 'TEST_%')");
        jdbcTemplate.update("delete from staff_roles where code like 'TEST_%'");
        jdbcTemplate.update("delete from staff_role_permissions where staff_role_id = "
                + "(select id from staff_roles where code = 'AUDITOR') and permission_code <> 'audit.read'");
    }

    private void role(String code) {
        jdbcTemplate.update("insert into staff_roles (id, code, name, created_at) values (?, ?, ?, now())",
                UUID.randomUUID(), code, code);
    }

    private static StaffPrincipal actor(String... roles) {
        return new StaffPrincipal(UUID.randomUUID(), "sub-" + UUID.randomUUID(), "staff@iskoala.com",
                "Deneme", Set.of(roles), Set.of());
    }

    @Test
    void aLeafRoleResolvesToItsOwnPermissionsOnly() {
        StaffAuthorization resolved = staffAuthorizationService.resolve(List.of("SUPPORT_AGENT"));

        assertEquals(Set.of("SUPPORT_AGENT"), resolved.roles());
        assertEquals(Set.of(StaffPermission.USER_READ), resolved.permissions());
    }

    @Test
    void aCompositeRoleWalksTheGraphDownwards() {
        StaffAuthorization resolved = staffAuthorizationService.resolve(List.of("TRUST_LEAD"));

        assertTrue(resolved.roles().containsAll(
                Set.of("TRUST_LEAD", "MODERATOR", "DISPUTE_HANDLER", "SUPPORT_AGENT")));
        assertEquals(Set.of(StaffPermission.USER_READ, StaffPermission.USER_READ_PII,
                        StaffPermission.CONTENT_MODERATE, StaffPermission.DISPUTE_RESOLVE),
                resolved.permissions());
    }

    @Test
    void theOwnerReachesEveryBranch() {
        Set<StaffPermission> permissions = staffAuthorizationService.resolve(List.of("OWNER")).permissions();

        assertTrue(permissions.containsAll(Set.of(StaffPermission.PAYOUT_RELEASE, StaffPermission.PAYMENT_REFUND,
                StaffPermission.CONTENT_MODERATE, StaffPermission.CATEGORY_MANAGE, StaffPermission.ROLE_MANAGE,
                StaffPermission.STAFF_MANAGE, StaffPermission.AUDIT_READ, StaffPermission.USER_READ_PII)));
    }

    @Test
    void aTrustRoleNeverReachesTheFinanceBranch() {
        Set<StaffPermission> permissions = staffAuthorizationService.resolve(List.of("TRUST_LEAD")).permissions();

        assertFalse(permissions.contains(StaffPermission.PAYOUT_RELEASE));
        assertFalse(permissions.contains(StaffPermission.PAYMENT_REFUND));
        assertFalse(permissions.contains(StaffPermission.FINANCE_READ));
    }

    @Test
    void aRoleTheTokenCarriesButKoalaDoesNotKnowIsSkipped() {
        StaffAuthorization resolved = staffAuthorizationService.resolve(List.of("SUPPORT_AGENT", "NOT_A_ROLE"));

        assertEquals(Set.of(StaffPermission.USER_READ), resolved.permissions());
        assertFalse(resolved.roles().contains("NOT_A_ROLE"));
    }

    @Test
    void anUnknownPermissionCodeInTheGraphIsSkippedInsteadOfBreakingResolution() {
        jdbcTemplate.update("insert into staff_role_permissions (id, staff_role_id, permission_code, created_at) "
                        + "select ?, id, 'not.a.permission', now() from staff_roles where code = 'AUDITOR'",
                UUID.randomUUID());

        assertEquals(Set.of(StaffPermission.AUDIT_READ),
                staffAuthorizationService.resolve(List.of("AUDITOR")).permissions());
    }

    @Test
    void aPermissionYouDoNotHoldCannotBeGranted() {
        role("TEST_TARGET");

        StaffPrincipal moderator = actor("MODERATOR");

        assertThrows(ForbiddenException.class,
                () -> staffAuthorizationService.grantPermission(moderator, "TEST_TARGET", StaffPermission.PAYOUT_RELEASE));
    }

    @Test
    void roleManagementAloneIsNotEnoughToGrantSomethingYouDoNotHold() {
        role("TEST_TARGET");
        role("TEST_ROLE_ADMIN");
        jdbcTemplate.update("insert into staff_role_permissions (id, staff_role_id, permission_code, created_at) "
                        + "select ?, id, 'role.manage', now() from staff_roles where code = 'TEST_ROLE_ADMIN'",
                UUID.randomUUID());

        StaffPrincipal roleAdmin = actor("TEST_ROLE_ADMIN");

        assertThrows(ForbiddenException.class,
                () -> staffAuthorizationService.grantPermission(roleAdmin, "TEST_TARGET", StaffPermission.PAYOUT_RELEASE));
    }

    @Test
    void theOwnerCanGrantWhatTheOwnerHolds() {
        role("TEST_TARGET");

        staffAuthorizationService.grantPermission(actor("OWNER"), "TEST_TARGET", StaffPermission.CATEGORY_MANAGE);

        assertEquals(Set.of(StaffPermission.CATEGORY_MANAGE),
                staffAuthorizationService.resolve(List.of("TEST_TARGET")).permissions());
    }

    @Test
    void anInclusionThatWouldCloseALoopIsRefused() {
        role("TEST_PARENT");
        role("TEST_CHILD");
        staffAuthorizationService.includeRole(actor("OWNER"), "TEST_PARENT", "TEST_CHILD");

        StaffPrincipal owner = actor("OWNER");

        assertThrows(ConflictException.class,
                () -> staffAuthorizationService.includeRole(owner, "TEST_CHILD", "TEST_PARENT"));
    }

    @Test
    void anActorWithoutAStaffRecordCannotWriteTheGraph() {
        role("TEST_TARGET");

        StaffPrincipal stranger = StaffPrincipal.unregistered("sub-stranger", Set.of("OWNER"));

        assertThrows(ForbiddenException.class,
                () -> staffAuthorizationService.grantPermission(stranger, "TEST_TARGET", StaffPermission.CATEGORY_MANAGE));
    }
}
