package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.security.StaffAuditRecord;
import com.exskylab.koala.entities.AdminAuditEntry;
import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminAuditRetentionIT extends AbstractIntegrationTest {

    @Autowired
    private StaffAuditService staffAuditService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void emptyTheTrail() {
        jdbcTemplate.update("delete from admin_audit_entries");
    }

    private static StaffAuditRecord record(PermissionSensitivity sensitivity, String resourceId) {
        return new StaffAuditRecord(UUID.randomUUID(), "Saklama Denemesi", "payout.release",
                "payout.release", "job-assignments", resourceId, true, true, sensitivity,
                "203.0.113.9", "koala-admin/1.0", com.exskylab.koala.core.security.AuditSubjects.NONE);
    }

    private UUID agedEntry(String retainInterval, String purgeInterval, String resourceId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into admin_audit_entries (id, occurred_at, actor_staff_id, actor_display_name,
                                                 permission, action, resource_type, resource_id,
                                                 allowed, mutating, sensitivity, retain_until)
                values (?, now(), ?, 'Saklama Denemesi', 'user.read', 'user.read', 'users', ?,
                        true, false, 'NORMAL', now() + (?)::interval)
                """, id, UUID.randomUUID(), resourceId, retainInterval);
        jdbcTemplate.update("""
                insert into admin_audit_entry_details (audit_entry_id, request_body, ip, user_agent, purge_after)
                values (?, '{"query":"biri@iskoala.com"}', '203.0.113.9', 'koala-admin/1.0',
                        now() + (?)::interval)
                """, id, purgeInterval);
        return id;
    }

    private int entries() {
        return jdbcTemplate.queryForObject("select count(*) from admin_audit_entries", Integer.class);
    }

    private int details() {
        return jdbcTemplate.queryForObject("select count(*) from admin_audit_entry_details", Integer.class);
    }

    @Test
    void aFinancialRowIsKeptTenYearsAndAnyOtherTwentyFourMonths() {
        AdminAuditEntry financial = staffAuditService.record(
                record(PermissionSensitivity.FINANCIAL, "a-1"), null, null, null);
        AdminAuditEntry normal = staffAuditService.record(
                record(PermissionSensitivity.NORMAL, "a-2"), null, null, null);

        assertEquals(10, yearsBetween(financial.getId()));
        assertEquals(2, yearsBetween(normal.getId()));
    }

    @Test
    void theDeletableHalfIsDatedSixMonthsOut() {
        AdminAuditEntry entry = staffAuditService.record(
                record(PermissionSensitivity.FINANCIAL, "a-3"), "{}", null, null);

        Integer months = jdbcTemplate.queryForObject("""
                select (extract(year from age(d.purge_after, e.occurred_at)) * 12
                      + extract(month from age(d.purge_after, e.occurred_at)))::int
                from admin_audit_entries e
                join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.id = ?
                """, Integer.class, entry.getId());

        assertEquals(6, months);
    }

    @Test
    void anExpiredDetailIsPurgedWhileItsCoreStays() {
        agedEntry("24 months", "-1 day", "u-1");

        assertEquals(1, staffAuditService.purgeExpiredDetails());

        assertEquals(1, entries(), "Çekirdek 24 ay dolmadan silinemez.");
        assertEquals(0, details());
    }

    @Test
    void anExpiredCoreRowTakesItsDetailWithIt() {
        agedEntry("-1 day", "6 months", "u-2");

        assertEquals(1, staffAuditService.purgeExpiredEntries());

        assertEquals(0, entries());
        assertEquals(0, details());
    }

    @Test
    void aRowInsideBothWindowsIsLeftAlone() {
        agedEntry("24 months", "6 months", "u-3");

        assertEquals(0, staffAuditService.purgeExpiredDetails());
        assertEquals(0, staffAuditService.purgeExpiredEntries());

        assertEquals(1, entries());
        assertEquals(1, details());
    }

    @Test
    void anErasureRequestDeletesTheDetailAndLeavesTheCore() {
        agedEntry("24 months", "6 months", "u-4");
        agedEntry("24 months", "6 months", "u-4");
        agedEntry("24 months", "6 months", "u-5");

        assertEquals(2, staffAuditService.eraseDetailsForResource("u-4"));

        assertEquals(3, entries(), "KVKK silme talebi çekirdeği silmez.");
        assertEquals(1, details(), "Yalnızca talebi eden kişinin detayları gitmeliydi.");
    }

    @Test
    void erasingTwiceIsHarmless() {
        agedEntry("24 months", "6 months", "u-6");

        assertEquals(1, staffAuditService.eraseDetailsForResource("u-6"));
        assertEquals(0, staffAuditService.eraseDetailsForResource("u-6"));
        assertEquals(1, entries());
    }

    private int yearsBetween(UUID entryId) {
        return jdbcTemplate.queryForObject(
                "select extract(year from age(retain_until, occurred_at))::int "
                        + "from admin_audit_entries where id = ?", Integer.class, entryId);
    }
}
