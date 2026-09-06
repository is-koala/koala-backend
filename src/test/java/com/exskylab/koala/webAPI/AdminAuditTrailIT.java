package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import com.exskylab.koala.support.StaffTokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminAuditTrailIT extends AbstractStaffIntegrationTest {

    private static final String LATE_FEE_KEY = "cancellation.late-fee-rate";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTheTrail() {
        jdbcTemplate.update("delete from admin_audit_entries");
    }

    @AfterEach
    void removeTestData() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'audit-%@iskoala.com'");
        jdbcTemplate.update("delete from app_settings where setting_key = ?", LATE_FEE_KEY);
    }

    private UUID staffMember(String subject) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into staff_members (id, keycloak_subject, email, display_name, is_active, created_at)
                values (?, ?, ?, ?, true, now())
                """, id, subject, "audit-" + subject + "@iskoala.com", "Denetim Denemesi");
        return id;
    }

    private String tokenFor(String role) {
        String subject = UUID.randomUUID().toString();
        staffMember(subject);
        return StaffTokenIssuer.tokenFor(subject, List.of(role));
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("""
                select e.*, d.request_body, d.ip, d.user_agent, d.before_value, d.after_value, d.purge_after
                from admin_audit_entries e
                left join admin_audit_entry_details d on d.audit_entry_id = e.id
                order by e.occurred_at
                """);
    }

    private Map<String, Object> onlyRow() {
        List<Map<String, Object>> rows = rows();
        assertEquals(1, rows.size(), "Tam olarak bir denetim satırı bekleniyordu: " + rows);
        return rows.getFirst();
    }

    @Test
    void anAllowedChangeIsWrittenWithItsActorPermissionAndRecord() throws Exception {
        String subject = UUID.randomUUID().toString();
        UUID staffId = staffMember(subject);
        String token = StaffTokenIssuer.tokenFor(subject, List.of("OWNER"));

        mockMvc.perform(put("/admin/settings/" + LATE_FEE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", "koala-admin/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.40\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = onlyRow();

        assertEquals(staffId, row.get("actor_staff_id"));
        assertEquals("settings.manage", row.get("permission"));
        assertEquals("setting.update", row.get("action"));
        assertEquals("settings", row.get("resource_type"));
        assertEquals(LATE_FEE_KEY, row.get("resource_id"));
        assertEquals(Boolean.TRUE, row.get("allowed"));
        assertEquals(Boolean.TRUE, row.get("mutating"));
        assertEquals("NORMAL", row.get("sensitivity"));
        assertNotNull(row.get("occurred_at"));
        assertEquals("Denetim Denemesi", row.get("actor_display_name"));
    }

    @Test
    void theDeletableHalfCarriesTheBodyTheAddressAndTheClient() throws Exception {
        String token = tokenFor("OWNER");

        mockMvc.perform(put("/admin/settings/" + LATE_FEE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", "koala-admin/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.40\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = onlyRow();

        assertEquals("{\"value\":\"0.40\"}", row.get("request_body"));
        assertEquals("koala-admin/1.0", row.get("user_agent"));
        assertNotNull(row.get("ip"));
        assertNotNull(row.get("purge_after"));
    }

    @Test
    void aDeniedRequestIsTheOnlyTraceOfAnAttemptedEscalation() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");

        mockMvc.perform(put("/admin/settings/" + LATE_FEE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.90\"}"))
                .andExpect(status().isForbidden());

        Map<String, Object> row = onlyRow();

        assertEquals(Boolean.FALSE, row.get("allowed"));
        assertEquals("settings.manage", row.get("permission"));
        assertEquals("setting.update", row.get("action"));
    }

    @Test
    void aFilteredUserSearchIsLoggedBecauseTheQueryItselfIsPersonalData() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");

        mockMvc.perform(get("/admin/users").param("query", "biri@iskoala.com")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Map<String, Object> row = onlyRow();

        assertEquals("user.read", row.get("permission"));
        assertEquals("user.read", row.get("action"));
        assertEquals("users", row.get("resource_type"));
        assertEquals("", row.get("resource_id"));
        assertEquals(Boolean.FALSE, row.get("mutating"));
    }

    @Test
    void loadingTheListWithoutAFilterIsNotLogged() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(rows().isEmpty(), "Boş liste yüklemesi loglanmaz: " + rows());
    }

    @Test
    void turningThePageIsNotASearch() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");

        mockMvc.perform(get("/admin/users").param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(rows().isEmpty(), "Sayfalama parametresi filtre değildir: " + rows());
    }

    @Test
    void readingASingleRecordIsLoggedEvenWithoutAFilter() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        Map<String, Object> row = onlyRow();

        assertEquals(userId.toString(), row.get("resource_id"),
                "Hangi kaydın okunduğu çekirdekte durmalı.");
        assertEquals(Boolean.TRUE, row.get("allowed"),
                "Yetki verildi; kaydın bulunamaması ayrı bir sorun.");
    }

    @Test
    void aFinancialActionIsKeptFarLongerThanANormalOne() throws Exception {
        String financeDenied = tokenFor("SUPPORT_AGENT");
        String owner = tokenFor("OWNER");

        mockMvc.perform(post("/admin/job-assignments/" + UUID.randomUUID() + "/release")
                        .header("Authorization", "Bearer " + financeDenied))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/admin/settings/" + LATE_FEE_KEY)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.40\"}"))
                .andExpect(status().isOk());

        Integer financialYears = jdbcTemplate.queryForObject(
                "select extract(year from age(retain_until, occurred_at))::int "
                        + "from admin_audit_entries where sensitivity = 'FINANCIAL'", Integer.class);
        Integer normalYears = jdbcTemplate.queryForObject(
                "select extract(year from age(retain_until, occurred_at))::int "
                        + "from admin_audit_entries where sensitivity = 'NORMAL'", Integer.class);

        assertEquals(10, financialYears, "Finansal denetim satırı 10 yıl saklanır.");
        assertEquals(2, normalYears, "Diğerleri 24 ay.");
    }

    @Test
    void anAnonymousProbeNeverFillsTheTable() throws Exception {
        mockMvc.perform(get("/admin/users").param("query", "biri@iskoala.com"))
                .andExpect(status().isUnauthorized());

        assertTrue(rows().isEmpty(),
                "Kimliksiz istek denetim tablosunu doldurmamalı: " + rows());
    }

    @Test
    void theMarketplaceBridgeAdminIsAuditedToo() throws Exception {
        mockMvc.perform(get("/admin/users").param("query", "biri@iskoala.com")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        Map<String, Object> row = onlyRow();

        assertEquals(Boolean.TRUE, row.get("allowed"));
        assertEquals("admin", row.get("actor_display_name"));
    }
}
