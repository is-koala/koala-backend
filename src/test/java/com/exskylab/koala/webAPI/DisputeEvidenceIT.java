package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.helpers.Checksums;
import com.exskylab.koala.core.privacy.AccountAnonymizer;
import com.exskylab.koala.core.privacy.PersonalMediaDeletionOutbox;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.exskylab.koala.support.ImageMetadataFixtures.BODY_SERIAL;
import static com.exskylab.koala.support.ImageMetadataFixtures.CAMERA_MAKE;
import static com.exskylab.koala.support.ImageMetadataFixtures.IMAGE_UNIQUE_ID;
import static com.exskylab.koala.support.ImageMetadataFixtures.LENS_SERIAL;
import static com.exskylab.koala.support.ImageMetadataFixtures.containsAscii;
import static com.exskylab.koala.support.ImageMetadataFixtures.jpegWithLocationAndSerials;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static final byte[] WEBP = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StorageService storageService;

    @Autowired
    private PersonalMediaDeletionOutbox mediaDeletionOutbox;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountAnonymizer accountAnonymizer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> storedKeys = new ArrayList<>();

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID companyContactId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID disputeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.update("delete from admin_audit_entries");
        insertUser(employerId);
        insertUser(workerId);
        insertUser(companyContactId);
        insertUser(operatorId);

        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Kanıt Şirketi " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "evidence-company-" + companyId + "@example.com");
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, 'STAFF')",
                UUID.randomUUID(), companyId, companyContactId);

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
        storedKeys.addAll(jdbc.queryForList("select storage_key from dispute_evidence where dispute_id = ? "
                + "and storage_key is not null", String.class, disputeId));
        for (String key : storedKeys) {
            jdbc.update("delete from account_media_deletion_outbox where storage_key = ?", key);
        }
        jdbc.update("delete from dispute_evidence where dispute_id = ?", disputeId);
        jdbc.update("delete from disputes where id = ?", disputeId);
        jdbc.update("delete from job_assignments where id = ?", assignmentId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?, ?)",
                employerId, workerId, companyContactId, operatorId);
        for (UUID id : List.of(employerId, workerId, companyContactId, operatorId)) {
            jdbc.update("delete from account_anonymization_audit where request_id in "
                    + "(select id from account_deletion_requests where user_id = ?)", id);
            jdbc.update("delete from account_deletion_requests where user_id = ?", id);
        }
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?)",
                employerId, workerId, companyContactId, operatorId);
        jdbc.update("delete from admin_audit_entries");
        jdbc.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'evidence-%@iskoala.com')");
        deleteStaffMembersLike("evidence-%@iskoala.com");
        jdbc.update("delete from users where id in (?, ?, ?, ?)",
                employerId, workerId, companyContactId, operatorId);
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
                .andExpect(jsonPath("$.data[0].uploaderSide").doesNotExist())
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist());

        mockMvc.perform(get("/admin/disputes/{id}/evidence", disputeId)
                        .header("Authorization", "Bearer " + staffTokenFor(
                                "evidence-" + UUID.randomUUID() + "@iskoala.com",
                                "Uyuşmazlık Uzmanı", "DISPUTE_HANDLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].uploaderSide").value("WORKER"))
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist());
    }

    @Test
    void staffSeesAFormerCompanyContactUploaderAsTheEmployerSide() throws Exception {
        jdbc.update("update jobs set company_id = ?, employer_type = 'COMPANY' where id = ?", companyId, jobId);
        attachAs(companyContactId).andExpect(status().isCreated());
        jdbc.update("delete from company_contacts where company_id = ? and user_id = ?", companyId, companyContactId);

        mockMvc.perform(get("/admin/disputes/{id}/evidence", disputeId)
                        .header("Authorization", "Bearer " + staffTokenFor(
                                "evidence-" + UUID.randomUUID() + "@iskoala.com",
                                "Uyuşmazlık Uzmanı", "DISPUTE_HANDLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].uploaderSide").value("EMPLOYER"));
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
    void aWebPPhotoIsRefusedWithAnUnsupportedTypeErrorThatNamesTheAcceptedFormats() throws Exception {
        mockMvc.perform(multipart("/disputes/{id}/evidence", disputeId)
                        .file(new MockMultipartFile("file", "kanit.webp", "image/webp", WEBP))
                        .with(user(principal(workerId, Role.ROLE_USER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STORAGE_UNSUPPORTED_TYPE"))
                .andExpect(jsonPath("$.message").value(containsString("PNG ya da JPEG")))
                .andExpect(jsonPath("$.message").value(containsString("WebP")));

        mockMvc.perform(multipart("/images")
                        .file(new MockMultipartFile("image", "foto.webp", "image/webp", WEBP))
                        .with(user(principal(workerId, Role.ROLE_USER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STORAGE_UNSUPPORTED_TYPE"))
                .andExpect(jsonPath("$.message").value(containsString("PNG ya da JPEG")));

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from dispute_evidence where dispute_id = ?", Integer.class, disputeId));
    }

    @Test
    void aPhotoCarryingLocationAndSerialNumbersIsStoredAndDownloadedWithoutThem() throws Exception {
        byte[] original = jpegWithLocationAndSerials(64, 48);
        assertTrue(containsAscii(original, BODY_SERIAL), "the fixture must carry what the server has to remove");

        JsonNode attached = data(mockMvc.perform(multipart("/disputes/{id}/evidence", disputeId)
                        .file(new MockMultipartFile("file", "evden.jpg", "image/jpeg", original))
                        .with(user(principal(workerId, Role.ROLE_USER))))
                .andExpect(status().isCreated()));
        UUID evidenceId = UUID.fromString(attached.get("id").asText());

        byte[] downloaded = mockMvc.perform(get("/disputes/{id}/evidence/{evidenceId}/download", disputeId, evidenceId)
                        .with(user(principal(employerId, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        for (String leaked : List.of("Exif\0\0", CAMERA_MAKE, BODY_SERIAL, LENS_SERIAL, IMAGE_UNIQUE_ID)) {
            assertFalse(containsAscii(downloaded, leaked), "the other party downloads " + leaked.trim());
        }
        assertNotNull(ImageIO.read(new ByteArrayInputStream(downloaded)));
        assertEquals(Checksums.sha256Hex(downloaded), attached.get("checksumSha256").asText(),
                "the checksum must describe the bytes that are stored and served");
        assertEquals(downloaded.length, attached.get("fileSize").asLong());
        assertNotNull(jdbc.queryForObject("select metadata_scrubbed_at from dispute_evidence where id = ?",
                Object.class, evidenceId));
    }

    @Test
    void aPdfIsStoredByteForByteBecauseOnlyImagesAreReEncoded() throws Exception {
        byte[] pdf = "%PDF-1.4\n1 0 obj << /Author (Kanit Sahibi) >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n"
                .getBytes(StandardCharsets.US_ASCII);

        JsonNode attached = data(mockMvc.perform(multipart("/disputes/{id}/evidence", disputeId)
                        .file(new MockMultipartFile("file", "dekont.pdf", "application/pdf", pdf))
                        .with(user(principal(workerId, Role.ROLE_USER))))
                .andExpect(status().isCreated()));
        UUID evidenceId = UUID.fromString(attached.get("id").asText());

        byte[] downloaded = mockMvc.perform(get("/disputes/{id}/evidence/{evidenceId}/download", disputeId, evidenceId)
                        .with(user(principal(employerId, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(pdf, downloaded);
        assertNull(jdbc.queryForObject("select metadata_scrubbed_at from dispute_evidence where id = ?",
                Object.class, evidenceId));
    }

    @Test
    void theBackfillScrubsAStoredPhotoKeepsTheEvidenceAndQueuesTheOldObject() throws Exception {
        byte[] raw = jpegWithLocationAndSerials(40, 30);
        UUID evidenceId = storeRawEvidence(raw, Checksums.sha256Hex(raw));
        String rawKey = storageKeyOf(evidenceId);
        UUID staffId = createStaffMember("evidence-" + UUID.randomUUID() + "@iskoala.com", "Güven Lideri", "TRUST_LEAD");

        JsonNode report = data(mockMvc.perform(post("/admin/disputes/evidence/metadata-scrub")
                        .param("limit", "100")
                        .header("Authorization", "Bearer " + staffAccessToken(staffId)))
                .andExpect(status().isOk()));
        assertFalse(report.get("failed").toString().contains(evidenceId.toString()), report.toString());

        Map<String, Object> row = jdbc.queryForMap("select storage_key, file_size, checksum_sha256, "
                + "pre_scrub_file_size, pre_scrub_checksum_sha256, metadata_scrubbed_at, "
                + "metadata_scrubbed_by_staff_id, metadata_scrub_error from dispute_evidence where id = ?", evidenceId);
        String cleanKey = (String) row.get("storage_key");
        assertNotEquals(rawKey, cleanKey, "the scrubbed copy is a new object; the original is never overwritten");
        byte[] clean = storageService.readFile(cleanKey);
        assertFalse(containsAscii(clean, BODY_SERIAL));
        assertFalse(containsAscii(clean, "Exif\0\0"));
        assertEquals(Checksums.sha256Hex(clean), row.get("checksum_sha256"));
        assertEquals((long) clean.length, ((Number) row.get("file_size")).longValue());
        assertEquals(Checksums.sha256Hex(raw), row.get("pre_scrub_checksum_sha256"));
        assertEquals((long) raw.length, ((Number) row.get("pre_scrub_file_size")).longValue());
        assertNotNull(row.get("metadata_scrubbed_at"));
        assertEquals(staffId, row.get("metadata_scrubbed_by_staff_id"));
        assertNull(row.get("metadata_scrub_error"));

        byte[] downloaded = mockMvc.perform(get("/disputes/{id}/evidence/{evidenceId}/download", disputeId, evidenceId)
                        .with(user(principal(employerId, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(clean, downloaded);

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from account_media_deletion_outbox where storage_key = ?", Integer.class, rawKey));
        assertArrayEquals(raw, storageService.readFile(rawKey), "the original stays until the outbox removes it");
        mediaDeletionOutbox.process(List.of(rawKey));
        assertThrows(RuntimeException.class, () -> storageService.readFile(rawKey));

        assertEquals(1, jdbc.queryForObject("select count(*) from admin_audit_entries "
                + "where action = 'dispute.evidence.scrub' and allowed = true", Integer.class));

        data(mockMvc.perform(post("/admin/disputes/evidence/metadata-scrub")
                        .param("limit", "100")
                        .header("Authorization", "Bearer " + staffAccessToken(staffId)))
                .andExpect(status().isOk()));
        assertEquals(cleanKey, storageKeyOf(evidenceId), "a scrubbed photo is never rewritten a second time");
    }

    @Test
    void theBackfillLeavesAPhotoWhoseBytesNoLongerMatchItsChecksumUntouched() throws Exception {
        byte[] raw = jpegWithLocationAndSerials(40, 30);
        String recorded = "0".repeat(64);
        UUID evidenceId = storeRawEvidence(raw, recorded);
        String rawKey = storageKeyOf(evidenceId);
        String token = staffTokenFor("evidence-" + UUID.randomUUID() + "@iskoala.com", "Güven Lideri", "TRUST_LEAD");

        JsonNode report = data(mockMvc.perform(post("/admin/disputes/evidence/metadata-scrub")
                        .param("limit", "100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertTrue(report.get("failed").toString().contains(evidenceId + "\",\"reason\":\"CHECKSUM_MISMATCH"),
                report.toString());
        Map<String, Object> row = jdbc.queryForMap("select storage_key, checksum_sha256, metadata_scrubbed_at, "
                + "metadata_scrub_error from dispute_evidence where id = ?", evidenceId);
        assertEquals(rawKey, row.get("storage_key"));
        assertEquals(recorded, row.get("checksum_sha256"));
        assertNull(row.get("metadata_scrubbed_at"));
        assertEquals("CHECKSUM_MISMATCH", row.get("metadata_scrub_error"));
        assertArrayEquals(raw, storageService.readFile(rawKey));

        JsonNode again = data(mockMvc.perform(post("/admin/disputes/evidence/metadata-scrub")
                        .param("limit", "100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertFalse(again.get("failed").toString().contains(evidenceId.toString()),
                "a failed photo is not retried unless asked: " + again);
        JsonNode retried = data(mockMvc.perform(post("/admin/disputes/evidence/metadata-scrub")
                        .param("limit", "100").param("retryFailed", "true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertTrue(retried.get("failed").toString().contains(evidenceId.toString()), retried.toString());
    }

    @Test
    void anonymizingTheUploaderAlsoErasesTheChecksumOfThePhotoBeforeItWasScrubbed() throws Exception {
        byte[] raw = jpegWithLocationAndSerials(40, 30);
        UUID evidenceId = storeRawEvidence(raw, Checksums.sha256Hex(raw));
        mockMvc.perform(post("/admin/disputes/evidence/metadata-scrub").param("limit", "100")
                        .header("Authorization", "Bearer " + staffTokenFor(
                                "evidence-" + UUID.randomUUID() + "@iskoala.com", "Güven Lideri", "TRUST_LEAD")))
                .andExpect(status().isOk());
        storedKeys.add(storageKeyOf(evidenceId));
        assertNotNull(jdbc.queryForObject("select pre_scrub_checksum_sha256 from dispute_evidence where id = ?",
                String.class, evidenceId));

        UUID requestId = UUID.randomUUID();
        jdbc.update("insert into account_deletion_requests (id, user_id, subject_id, status, requested_at, created_at, "
                + "is_deleted) values (?, ?, ?, 'REQUESTED', now(), now(), false)", requestId, workerId, workerId);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accountAnonymizer.anonymize(workerId, requestId));

        Map<String, Object> row = jdbc.queryForMap("select checksum_sha256, pre_scrub_checksum_sha256, "
                + "pre_scrub_file_size, is_deleted from dispute_evidence where id = ?", evidenceId);
        assertEquals(Boolean.TRUE, row.get("is_deleted"));
        assertNull(row.get("checksum_sha256"));
        assertNull(row.get("pre_scrub_checksum_sha256"), "a hash of the original photo outlives the account");
        assertNull(row.get("pre_scrub_file_size"));
    }

    @Test
    void onlyTheDeletionPermissionMayRunTheBackfill() throws Exception {
        byte[] raw = jpegWithLocationAndSerials(40, 30);
        UUID evidenceId = storeRawEvidence(raw, Checksums.sha256Hex(raw));
        String rawKey = storageKeyOf(evidenceId);

        mockMvc.perform(post("/admin/disputes/evidence/metadata-scrub")
                        .header("Authorization", "Bearer " + staffTokenFor(
                                "evidence-" + UUID.randomUUID() + "@iskoala.com", "Uyuşmazlık Uzmanı", "DISPUTE_HANDLER")))
                .andExpect(status().isForbidden());
        assertEquals(rawKey, storageKeyOf(evidenceId));
    }

    private UUID storeRawEvidence(byte[] raw, String checksum) {
        String key = storageService.uploadFile(raw, "eski.jpg", "image/jpeg", FolderType.DISPUTE_EVIDENCE);
        storedKeys.add(key);
        UUID evidenceId = UUID.randomUUID();
        jdbc.update("insert into dispute_evidence (id, created_at, is_deleted, dispute_id, uploaded_by_id, storage_key, "
                        + "original_file_name, content_type, file_size, checksum_sha256) "
                        + "values (?, now(), false, ?, ?, ?, 'eski.jpg', 'image/jpeg', ?, ?)",
                evidenceId, disputeId, workerId, key, (long) raw.length, checksum);
        return evidenceId;
    }

    private String storageKeyOf(UUID evidenceId) {
        return jdbc.queryForObject("select storage_key from dispute_evidence where id = ?", String.class, evidenceId);
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
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

    private ResultActions attachAs(UUID actorId) throws Exception {
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
