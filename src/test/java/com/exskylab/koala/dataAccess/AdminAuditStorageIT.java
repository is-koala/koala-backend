package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminAuditStorageIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeTestRows() {
        jdbcTemplate.update("delete from admin_audit_entries where action like 'storage.test%'");
    }

    private UUID entry(String action, UUID actor) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into admin_audit_entries (id, occurred_at, actor_staff_id, actor_display_name,
                                                 permission, action, resource_type, resource_id,
                                                 allowed, mutating, sensitivity, retain_until)
                values (?, now(), ?, ?, 'settings.manage', ?, 'settings', ?, true, true, 'NORMAL',
                        now() + interval '24 months')
                """, id, actor, "Depo Denemesi", action, "cancellation.late-fee-rate");
        jdbcTemplate.update("""
                insert into admin_audit_entry_details (audit_entry_id, request_body, ip, user_agent,
                                                       before_value, after_value, purge_after)
                values (?, '{"value":"0.30"}', '203.0.113.7', 'koala-admin/1.0', '0.10', '0.30',
                        now() + interval '6 months')
                """, id);
        return id;
    }

    @Test
    void theCoreRowSurvivesTheDeletionOfItsDetail() {
        UUID id = entry("storage.test.erase", UUID.randomUUID());

        jdbcTemplate.update("delete from admin_audit_entry_details where audit_entry_id = ?", id);

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_entries where id = ?", Integer.class, id),
                "KVKK silme talebi detayı siler, çekirdek kalır.");
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_entry_details where audit_entry_id = ?", Integer.class, id));
    }

    @Test
    void whoDidWhatToWhichRecordIsNotStoredInTheDeletableHalf() {
        UUID actor = UUID.randomUUID();
        UUID id = entry("storage.test.core", actor);

        jdbcTemplate.update("delete from admin_audit_entry_details where audit_entry_id = ?", id);

        assertEquals("cancellation.late-fee-rate", jdbcTemplate.queryForObject(
                "select resource_id from admin_audit_entries where id = ?", String.class, id));
        assertEquals("storage.test.core", jdbcTemplate.queryForObject(
                "select action from admin_audit_entries where id = ?", String.class, id));
        assertEquals(actor, jdbcTemplate.queryForObject(
                "select actor_staff_id from admin_audit_entries where id = ?", UUID.class, id));
    }

    @Test
    void removingACoreRowTakesItsDetailWithIt() {
        UUID id = entry("storage.test.cascade", UUID.randomUUID());

        jdbcTemplate.update("delete from admin_audit_entries where id = ?", id);

        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_entry_details where audit_entry_id = ?", Integer.class, id),
                "Çekirdek satır saklama süresini doldurunca detayı da gitmeliydi.");
    }

    @Test
    void theTwoHalvesCarryTheirOwnRetentionDeadlines() {
        UUID id = entry("storage.test.retention", UUID.randomUUID());

        Boolean detailGoesFirst = jdbcTemplate.queryForObject("""
                select d.purge_after < e.retain_until
                from admin_audit_entries e
                join admin_audit_entry_details d on d.audit_entry_id = e.id
                where e.id = ?
                """, Boolean.class, id);

        assertEquals(Boolean.TRUE, detailGoesFirst,
                "Detay çekirdekten önce silinmeliydi.");
    }
}
