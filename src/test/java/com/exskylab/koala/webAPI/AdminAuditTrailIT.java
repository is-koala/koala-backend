package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    private UUID staffMember(String... roles) {
        return createStaffMember("audit-" + UUID.randomUUID() + "@iskoala.com", "Denetim Denemesi", roles);
    }

    private String tokenFor(String role) {
        return staffAccessToken(staffMember(role));
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
        UUID staffId = staffMember("OWNER");
        String token = staffAccessToken(staffId);

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
                        .header("X-Forwarded-For", "198.51.100.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.40\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = onlyRow();

        assertEquals("{\"value\":\"0.40\"}", row.get("request_body"));
        assertEquals("koala-admin/1.0", row.get("user_agent"));
        assertNotEquals("198.51.100.9", row.get("ip"),
                "Untrusted peers cannot spoof the audit address with forwarding headers.");
        assertNotNull(row.get("ip"));
        assertNotNull(row.get("purge_after"));
    }

    @Test
    void theLateFeeRateCanNoLongerBeChangedWithoutATrace() throws Exception {
        String token = tokenFor("OWNER");

        mockMvc.perform(put("/admin/settings/" + LATE_FEE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.40\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = onlyRow();

        assertEquals("0.40", row.get("after_value"));
        assertEquals(LATE_FEE_KEY, row.get("resource_id"));
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
    void theClosedMarketplaceBridgeIsRefusedAndTheRefusalIsAudited() throws Exception {
        mockMvc.perform(get("/admin/users").param("query", "biri@iskoala.com")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        Map<String, Object> row = onlyRow();

        assertEquals(Boolean.FALSE, row.get("allowed"));
        assertEquals("admin", row.get("actor_display_name"));
    }

    @Test
    void readingTheAuditLogIsRecordedButNeverReturnsItsOwnRow() throws Exception {
        String token = tokenFor("AUDITOR");

        mockMvc.perform(get("/admin/audit-log").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        Map<String, Object> row = onlyRow();

        assertEquals("audit.read", row.get("permission"), "Denetim kaydını okumak hassas bir okumadır.");
        assertEquals("audit-log", row.get("resource_type"));
        assertEquals(Boolean.FALSE, row.get("mutating"));
        assertEquals("PERSONAL_DATA", row.get("sensitivity"));
    }

    @Test
    void readingTheLogAgainAddsOneRowNotAnAvalanche() throws Exception {
        String token = tokenFor("AUDITOR");

        mockMvc.perform(get("/admin/audit-log").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        mockMvc.perform(get("/admin/audit-log").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        assertEquals(2, rows().size(),
                "İstek başına bir satır; okuma kendi satırını doğurup çoğaltmamalı.");
    }

    @Test
    void theAuditLogIsGuardedByItsOwnPermissionAndTheRefusalIsRecorded() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");

        mockMvc.perform(get("/admin/audit-log").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        Map<String, Object> row = onlyRow();

        assertEquals("audit.read", row.get("permission"));
        assertEquals(Boolean.FALSE, row.get("allowed"));
    }

    @Test
    void everyCoreFieldComesBackAndTheDetailRidesAlong() throws Exception {
        String owner = tokenFor("OWNER");
        String auditor = tokenFor("AUDITOR");

        mockMvc.perform(put("/admin/settings/" + LATE_FEE_KEY)
                        .header("Authorization", "Bearer " + owner)
                        .header("User-Agent", "koala-admin/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.40\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/audit-log").param("permission", "settings.manage")
                        .header("Authorization", "Bearer " + auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").exists())
                .andExpect(jsonPath("$.data.content[0].occurredAt").exists())
                .andExpect(jsonPath("$.data.content[0].actorStaffId").exists())
                .andExpect(jsonPath("$.data.content[0].actorDisplayName").value("Denetim Denemesi"))
                .andExpect(jsonPath("$.data.content[0].permission").value("settings.manage"))
                .andExpect(jsonPath("$.data.content[0].action").value("setting.update"))
                .andExpect(jsonPath("$.data.content[0].resourceType").value("settings"))
                .andExpect(jsonPath("$.data.content[0].resourceId").value(LATE_FEE_KEY))
                .andExpect(jsonPath("$.data.content[0].allowed").value(true))
                .andExpect(jsonPath("$.data.content[0].mutating").value(true))
                .andExpect(jsonPath("$.data.content[0].sensitivity").value("NORMAL"))
                .andExpect(jsonPath("$.data.content[0].requestBody").value("{\"value\":\"0.40\"}"))
                .andExpect(jsonPath("$.data.content[0].ip").exists())
                .andExpect(jsonPath("$.data.content[0].userAgent").value("koala-admin/1.0"))
                .andExpect(jsonPath("$.data.content[0].after").value("0.40"));
    }

    @Test
    void theCoreSurvivesWhenTheDetailIsErasedAndTheEndpointSaysSo() throws Exception {
        String owner = tokenFor("OWNER");
        String auditor = tokenFor("AUDITOR");

        mockMvc.perform(put("/admin/settings/" + LATE_FEE_KEY)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.40\"}"))
                .andExpect(status().isOk());

        jdbcTemplate.update("delete from admin_audit_entry_details");

        mockMvc.perform(get("/admin/audit-log").param("permission", "settings.manage")
                        .header("Authorization", "Bearer " + auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].action").value("setting.update"))
                .andExpect(jsonPath("$.data.content[0].requestBody").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].ip").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].userAgent").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].before").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].after").doesNotExist());
    }

    @Test
    void everyFilterNarrowsOnTheServer() throws Exception {
        String support = tokenFor("SUPPORT_AGENT");
        String auditor = tokenFor("AUDITOR");

        mockMvc.perform(get("/admin/users").param("query", "biri@iskoala.com")
                        .header("Authorization", "Bearer " + support))
                .andExpect(status().isOk());
        mockMvc.perform(put("/admin/settings/" + LATE_FEE_KEY)
                        .header("Authorization", "Bearer " + support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0.90\"}"))
                .andExpect(status().isForbidden());

        String header = "Bearer " + auditor;

        mockMvc.perform(get("/admin/audit-log").param("allowed", "false").header("Authorization", header))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].permission").value("settings.manage"));
        mockMvc.perform(get("/admin/audit-log").param("action", "user.read").header("Authorization", header))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        mockMvc.perform(get("/admin/audit-log").param("resourceType", "users").header("Authorization", header))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        mockMvc.perform(get("/admin/audit-log").param("resourceId", LATE_FEE_KEY).header("Authorization", header))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        mockMvc.perform(get("/admin/audit-log").param("from", "2000-01-01T00:00:00Z")
                        .param("to", "2000-01-02T00:00:00Z").header("Authorization", header))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void theActorFilterFindsWhatOnePersonDid() throws Exception {
        UUID staffId = staffMember("SUPPORT_AGENT");
        String support = staffAccessToken(staffId);
        String auditor = tokenFor("AUDITOR");

        mockMvc.perform(get("/admin/users").param("query", "biri@iskoala.com")
                        .header("Authorization", "Bearer " + support))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/audit-log").param("actor", staffId.toString())
                        .header("Authorization", "Bearer " + auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].actorStaffId").value(staffId.toString()));
    }

    @Test
    void anUnknownSortFieldIsARequestErrorNotAServerError() throws Exception {
        String token = tokenFor("AUDITOR");

        mockMvc.perform(get("/admin/audit-log").param("sort", "requestBody,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
