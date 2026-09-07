package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.core.security.StaffBootstrapResult;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffBootstrapIT extends AbstractIntegrationTest {

    private static final String OWNER_EMAIL = "bootstrap-owner@iskoala.com";
    private static final String OWNER_PASSWORD = "Bootstrap-Parolasi-2026";

    @Autowired
    private StaffAccountService staffAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void removeBootstrappedStaff() {
        jdbcTemplate.update("delete from admin_audit_entries where action = 'staff.bootstrap'");
        jdbcTemplate.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'bootstrap-%@iskoala.com')");
        jdbcTemplate.update("delete from staff_members where email like 'bootstrap-%@iskoala.com'");
    }

    private void emptyTheStaffTable() {
        jdbcTemplate.update("delete from staff_member_roles");
        jdbcTemplate.update("delete from staff_members");
    }

    @Test
    void anEmptyTableGetsItsFirstOwnerFromTheTwoEnvironmentVariables() {
        emptyTheStaffTable();

        StaffBootstrapResult result = staffAccountService.bootstrapOwner(OWNER_EMAIL, OWNER_PASSWORD);

        assertTrue(result.created(), "Boş tabloda ilk OWNER doğmalıydı");

        Map<String, Object> owner = jdbcTemplate.queryForMap(
                "select id, email, password_hash, must_change_password, is_active from staff_members");

        assertEquals(OWNER_EMAIL, owner.get("email"));
        assertTrue((Boolean) owner.get("must_change_password"),
                "Bootstrap hesabı ilk girişte parola değiştir bayrağıyla doğmalı");
        assertTrue((Boolean) owner.get("is_active"));
        assertTrue(passwordEncoder.matches(OWNER_PASSWORD, (String) owner.get("password_hash")),
                "Parola BCrypt ile saklanmalı");

        assertEquals(List.of("OWNER"), jdbcTemplate.queryForList("""
                select r.code from staff_member_roles m
                join staff_roles r on r.id = m.staff_role_id
                where m.staff_member_id = ?
                """, String.class, owner.get("id")));
    }

    @Test
    void theBootstrapLeavesItsOwnTraceInTheAuditLog() {
        emptyTheStaffTable();

        staffAccountService.bootstrapOwner(OWNER_EMAIL, OWNER_PASSWORD);

        Map<String, Object> entry = jdbcTemplate.queryForMap("""
                select e.action, e.permission, e.resource_type, e.actor_display_name, e.allowed,
                       e.mutating, d.after_value
                from admin_audit_entries e
                join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.action = 'staff.bootstrap'
                """);

        assertEquals("staff.manage", entry.get("permission"));
        assertEquals("staff", entry.get("resource_type"));
        assertEquals("bootstrap", entry.get("actor_display_name"));
        assertEquals(Boolean.TRUE, entry.get("allowed"));
        assertEquals(Boolean.TRUE, entry.get("mutating"));
        assertTrue(((String) entry.get("after_value")).contains("roles=[OWNER]"));
    }

    @Test
    void aTableThatAlreadyHasStaffIsNeverOverwrittenAndTheSkipIsReported() {
        emptyTheStaffTable();

        UUID existing = UUID.randomUUID();
        String hashBefore = passwordEncoder.encode("mevcut-personelin-parolasi");
        jdbcTemplate.update("""
                insert into staff_members (id, email, password_hash, display_name, is_active,
                                           must_change_password, failed_login_attempts, token_version, created_at)
                values (?, ?, ?, 'Mevcut Personel', true, false, 0, 0, now())
                """, existing, OWNER_EMAIL, hashBefore);

        StaffBootstrapResult result = staffAccountService.bootstrapOwner(OWNER_EMAIL, OWNER_PASSWORD);

        assertFalse(result.created(), "Dolu tabloda bootstrap hiçbir şey yaratmamalı");
        assertEquals(1, result.existingMembers(), "Atlanan bootstrap kaç personel gördüğünü söylemeli");
        assertEquals(1, (long) jdbcTemplate.queryForObject(
                "select count(*) from staff_members", Long.class));
        assertEquals(hashBefore, jdbcTemplate.queryForObject(
                "select password_hash from staff_members where id = ?", String.class, existing),
                "Var olan bir hesabın parolası asla ezilmemeli");
        assertEquals(0, (long) jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_entries where action = 'staff.bootstrap'", Long.class),
                "Atlanan bootstrap denetim satırı da açmamalı");
    }

    @Test
    void aSecondBootstrapOnAnAlreadyBootstrappedTableChangesNothing() {
        emptyTheStaffTable();

        staffAccountService.bootstrapOwner(OWNER_EMAIL, OWNER_PASSWORD);
        String hashAfterFirst = jdbcTemplate.queryForObject(
                "select password_hash from staff_members", String.class);

        StaffBootstrapResult second = staffAccountService.bootstrapOwner(
                "bootstrap-baskasi@iskoala.com", "Bambaska-Bir-Parola-2026");

        assertFalse(second.created());
        assertEquals(1, (long) jdbcTemplate.queryForObject(
                "select count(*) from staff_members", Long.class));
        assertEquals(hashAfterFirst, jdbcTemplate.queryForObject(
                "select password_hash from staff_members", String.class));
    }

    @Test
    void theBootstrappedOwnerCanActuallySignIn() {
        emptyTheStaffTable();

        staffAccountService.bootstrapOwner(OWNER_EMAIL, OWNER_PASSWORD);

        assertTrue(staffAccountService.login(OWNER_EMAIL, OWNER_PASSWORD).isMustChangePassword(),
                "İlk giriş parola değiştirme bayrağıyla dönmeli");
    }
}
