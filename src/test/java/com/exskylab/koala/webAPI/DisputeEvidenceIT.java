package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DisputeEvidenceIT extends AbstractStaffIntegrationTest {

    private static final byte[] PNG = png();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID disputeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.update("delete from admin_audit_entries");
        insertUser(employerId);
        insertUser(workerId);
        insertUser(operatorId);

        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Kanıt", "EVIDENCE_" + categoryId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, "
                        + "headcount, start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 1, now() - interval '2 hours', "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                jobId, employerId, categoryId, "Kanıt testi " + jobId);
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, work_hours, total_charged) "
                        + "values (?, now(), false, ?, ?, 'IN_PROGRESS', 'HELD_IN_ESCROW', 0, 110.00)",
                assignmentId, jobId, workerId);
        jdbc.update("insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, "
                        + "status, reason) values (?, now(), false, ?, ?, 'OPEN', ?)",
                disputeId, assignmentId, workerId, "Ücret eksik ödendi");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from dispute_evidence where dispute_id = ?", disputeId);
        jdbc.update("delete from disputes where id = ?", disputeId);
        jdbc.update("delete from job_assignments where id = ?", assignmentId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?)",
                employerId, workerId, operatorId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", employerId, workerId, operatorId);
        jdbc.update("delete from admin_audit_entries");
        jdbc.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'evidence-%@iskoala.com')");
        jdbc.update("delete from staff_members where email like 'evidence-%@iskoala.com'");
        jdbc.update("delete from users where id in (?, ?, ?)", employerId, workerId, operatorId);
    }

    @Test
    void aPartyAttachesEvidenceAndTheResponseCarriesNoStorageAddress() throws Exception {
        String body = attachAs(workerId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("kanit.png"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.checksumSha256").isNotEmpty())
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertFalse(body.contains("files/dispute-evidence"),
                "Depo anahtarı tele çıkmamalı: " + body);

        Map<String, Object> row = jdbc.queryForMap(
                "select storage_key, file_size, uploaded_by_id from dispute_evidence where dispute_id = ?",
                disputeId);
        String key = (String) row.get("storage_key");
        assertNotNull(key);
        assertTrue(key.startsWith("files/dispute-evidence/"), key);
        assertFalse(key.contains("kanit"), "Özgün ad depo anahtarına girmemeli: " + key);
        assertEquals((long) PNG.length, ((Number) row.get("file_size")).longValue());
        assertEquals(workerId, row.get("uploaded_by_id"));
    }

    @Test
    void theOtherPartyAndAnAuthorisedOperatorBothSeeTheSameEvidence() throws Exception {
        attachAs(workerId).andExpect(status().isCreated());

        mockMvc.perform(get("/disputes/{id}/evidence", disputeId).with(user(principal(employerId, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist());

        mockMvc.perform(get("/admin/disputes/{id}/evidence", disputeId)
                        .header("Authorization", "Bearer " + staffTokenFor(
                                "evidence-" + UUID.randomUUID() + "@iskoala.com",
                                "Uyuşmazlık Uzmanı", "DISPUTE_HANDLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist());
    }

    @Test
    void aPartyDownloadsTheEvidenceAsAnAttachmentThatIsNeverSniffed() throws Exception {
        attachAs(workerId).andExpect(status().isCreated());
        UUID evidenceId = jdbc.queryForObject(
                "select id from dispute_evidence where dispute_id = ?", UUID.class, disputeId);

        mockMvc.perform(get("/disputes/{id}/evidence/{evidenceId}/download", disputeId, evidenceId)
                        .with(user(principal(employerId, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String disposition = result.getResponse().getHeader("Content-Disposition");
                    assertNotNull(disposition);
                    assertTrue(disposition.startsWith("attachment;"), disposition);
                    assertEquals("nosniff", result.getResponse().getHeader("X-Content-Type-Options"));
                    assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
                });
    }

    @Test
    void aResolvedDisputeRefusesFurtherEvidenceAndKeepsWhatItAlreadyHas() throws Exception {
        attachAs(workerId).andExpect(status().isCreated());
        jdbc.update("update disputes set status = 'RESOLVED', resolution = 'DISMISSED', resolved_at = now() "
                + "where id = ?", disputeId);

        attachAs(workerId).andExpect(status().isConflict());

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from dispute_evidence where dispute_id = ? and is_deleted = false",
                Integer.class, disputeId));

        mockMvc.perform(get("/disputes/{id}/evidence", disputeId).with(user(principal(workerId, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void aRejectedUploadWritesNeitherARowNorAnObject() throws Exception {
        mockMvc.perform(multipart("/disputes/{id}/evidence", disputeId)
                        .file(new MockMultipartFile("file", "kanit.exe", "application/octet-stream",
                                new byte[]{0x4D, 0x5A, 0x00}))
                        .with(user(principal(workerId, Role.ROLE_USER))))
                .andExpect(status().isBadRequest());

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from dispute_evidence where dispute_id = ?", Integer.class, disputeId));
    }

    @Test
    void aStaffDecisionIsPersistedAgainstTheStaffAggregate() throws Exception {
        UUID staffId = createStaffMember("evidence-" + UUID.randomUUID() + "@iskoala.com",
                "Uyuşmazlık Uzmanı", "DISPUTE_HANDLER");

        mockMvc.perform(post("/admin/disputes/{id}/resolve", disputeId)
                        .header("Authorization", "Bearer " + staffAccessToken(staffId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"DISMISSED\",\"note\":\"Dayanaksız\"}"))
                .andExpect(status().isOk());

        Map<String, Object> decision = jdbc.queryForMap(
                "select resolved_by_id, resolved_by_staff_id from disputes where id = ?", disputeId);
        assertEquals(staffId, decision.get("resolved_by_staff_id"));
        assertNull(decision.get("resolved_by_id"));
    }

    @Test
    void aDeniedRefundResolutionLeavesATenYearFinancialAuditRecord() throws Exception {
        UUID staffId = createStaffMember("evidence-" + UUID.randomUUID() + "@iskoala.com",
                "Uyuşmazlık Uzmanı", "DISPUTE_HANDLER");

        mockMvc.perform(post("/admin/disputes/{id}/resolve", disputeId)
                        .header("Authorization", "Bearer " + staffAccessToken(staffId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"REFUND_EMPLOYER\",\"note\":\"İşveren haklı\"}"))
                .andExpect(status().isForbidden());

        Map<String, Object> audit = jdbc.queryForMap(
                "select permission, action, allowed, sensitivity, "
                        + "extract(year from age(retain_until, occurred_at))::int as retention_years "
                        + "from admin_audit_entries");
        assertEquals("payment.refund", audit.get("permission"));
        assertEquals("dispute.resolve.refund", audit.get("action"));
        assertEquals(Boolean.FALSE, audit.get("allowed"));
        assertEquals("FINANCIAL", audit.get("sensitivity"));
        assertEquals(10, ((Number) audit.get("retention_years")).intValue());
        assertEquals("OPEN", jdbc.queryForObject(
                "select status from disputes where id = ?", String.class, disputeId));
    }

    private org.springframework.test.web.servlet.ResultActions attachAs(UUID actorId) throws Exception {
        return mockMvc.perform(multipart("/disputes/{id}/evidence", disputeId)
                .file(new MockMultipartFile("file", "kanit.png", "image/png", PNG))
                .with(user(principal(actorId, Role.ROLE_USER))));
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                + "email_verified, phone_verified, identity_verified) "
                + "values (?, now(), false, ?, 'test', false, true, false, false)", id, email(id));
    }

    private String email(UUID id) {
        return id + "@example.com";
    }

    private UserPrincipal principal(UUID id, Role... roles) {
        return new UserPrincipal(id, email(id), Set.of(roles), null);
    }

    private static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(12, 9, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
