package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaffMembershipRulesIT extends AbstractIntegrationTest {

    @Autowired
    private StaffAccountService staffAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void emptyTheStaffTable() {
        jdbcTemplate.update("delete from staff_member_roles");
        jdbcTemplate.update("delete from staff_members");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from staff_member_roles");
        jdbcTemplate.update("delete from staff_members");
    }

    private UUID owner(String marker) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into staff_members (id, email, password_hash, display_name, is_active,
                                           must_change_password, failed_login_attempts, token_version, created_at)
                values (?, ?, ?, ?, true, false, 0, 0, now())
                """, id, "membership-" + marker + "@iskoala.com",
                passwordEncoder.encode("Uyelik-Kurali-Testi-2026"), "Sahip " + marker);

        jdbcTemplate.update("""
                insert into staff_member_roles (staff_member_id, staff_role_id, created_at)
                select ?, id, now() from staff_roles where code = 'OWNER'
                """, id);
        return id;
    }

    private StaffPrincipal principal(UUID staffId) {
        return staffAccountService.principalOf(staffId, 0).orElseThrow();
    }

    private List<String> rolesOf(UUID staffId) {
        return jdbcTemplate.queryForList("""
                select r.code from staff_member_roles m
                join staff_roles r on r.id = m.staff_role_id where m.staff_member_id = ?
                """, String.class, staffId);
    }

    @Test
    void theOnlyOwnerCannotDropTheirOwnAdministrationMembership() {
        UUID only = owner("tek");
        StaffPrincipal actor = principal(only);

        ConflictException refused = assertThrows(ConflictException.class,
                () -> staffAccountService.revokeRole(actor, only, "OWNER"));

        assertEquals("{staff.membership.self.lockout}", refused.getMessage());
        assertEquals(List.of("OWNER"), rolesOf(only), "Reddedilen yazım geri alınmalı");
    }

    @Test
    void anOwnerStillCannotDropTheirOwnMembershipWhenAnotherOwnerRemains() {
        UUID first = owner("birinci");
        owner("ikinci");
        StaffPrincipal actor = principal(first);

        assertThrows(ConflictException.class, () -> staffAccountService.revokeRole(actor, first, "OWNER"));
        assertEquals(List.of("OWNER"), rolesOf(first));
    }

    @Test
    void anOwnerMayDropAnotherOwnersMembershipWhileTheyThemselvesKeepIt() {
        UUID actorId = owner("aktor");
        UUID other = owner("digeri");

        assertDoesNotThrow(() -> staffAccountService.revokeRole(principal(actorId), other, "OWNER"));
        assertEquals(List.of(), rolesOf(other));
        assertEquals(List.of("OWNER"), rolesOf(actorId));
    }

    @Test
    void theLastActiveAdministratorCannotBeDeactivated() {
        UUID only = owner("tek");
        UUID clerk = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into staff_members (id, email, password_hash, display_name, is_active,
                                           must_change_password, failed_login_attempts, token_version, created_at)
                values (?, 'membership-katip@iskoala.com', ?, 'Katip', true, false, 0, 0, now())
                """, clerk, passwordEncoder.encode("Uyelik-Kurali-Testi-2026"));
        StaffPrincipal actor = principal(only);

        ConflictException refused = assertThrows(ConflictException.class,
                () -> staffAccountService.setActive(actor, only, false));

        assertEquals("{staff.membership.self.lockout}", refused.getMessage());
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "select is_active from staff_members where id = ?", Boolean.class, only));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "select is_active from staff_members where id = ?", Boolean.class, clerk));
    }

    @Test
    void deactivatingTheSecondToLastAdministratorIsAllowedButNotTheLast() {
        UUID actorId = owner("aktor");
        UUID other = owner("digeri");
        StaffPrincipal actor = principal(actorId);

        assertDoesNotThrow(() -> staffAccountService.setActive(actor, other, false));

        ConflictException refused = assertThrows(ConflictException.class,
                () -> staffAccountService.setActive(actor, actorId, false));
        assertEquals("{staff.membership.self.lockout}", refused.getMessage());
    }

    @Test
    void theSystemNeverRunsOutOfAdministratorsEvenWhenSomeoneElseIsBeingDeactivated() {
        UUID lastOwner = owner("son-sahip");
        UUID clerk = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into staff_members (id, email, password_hash, display_name, is_active,
                                           must_change_password, failed_login_attempts, token_version, created_at)
                values (?, 'membership-personel-yoneticisi@iskoala.com', ?, 'Personel Yöneticisi',
                        true, false, 0, 0, now())
                """, clerk, passwordEncoder.encode("Uyelik-Kurali-Testi-2026"));
        jdbcTemplate.update("""
                insert into staff_member_roles (staff_member_id, staff_role_id, created_at)
                select ?, id, now() from staff_roles where code = 'AUDITOR'
                """, clerk);
        jdbcTemplate.update("""
                insert into staff_role_permissions (id, staff_role_id, permission_code, created_at)
                select ?, id, 'staff.manage', now() from staff_roles where code = 'AUDITOR'
                """, UUID.randomUUID());

        try {
            StaffPrincipal actor = principal(clerk);

            ConflictException refused = assertThrows(ConflictException.class,
                    () -> staffAccountService.setActive(actor, lastOwner, false));

            assertEquals("{staff.last.administrator}", refused.getMessage());
            assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                    "select is_active from staff_members where id = ?", Boolean.class, lastOwner));
        } finally {
            jdbcTemplate.update("delete from staff_role_permissions where permission_code = 'staff.manage' "
                    + "and staff_role_id = (select id from staff_roles where code = 'AUDITOR')");
        }
    }
}
