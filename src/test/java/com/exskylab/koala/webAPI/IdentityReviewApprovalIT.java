package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.dataAccess.IdentityVerificationDao;
import com.exskylab.koala.entities.IdentityVerification;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IdentityReviewApprovalIT extends AbstractStaffIntegrationTest {

    private static final String STAFF_EMAIL_PREFIX = "identity-approval-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IdentityDocumentReviewService identityDocumentReviewService;

    @Autowired
    private IdentityVerificationDao identityVerificationDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID userId = UUID.randomUUID();
    private final List<UUID> mediaIds = new ArrayList<>();
    private final List<UUID> reviewIds = new ArrayList<>();

    private UUID staffId;
    private String trustLeadToken;

    @BeforeEach
    void setUp() {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified, first_name, last_name) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false, 'kimlik', 'aday')",
                userId, userId + "@example.com");
        staffId = createStaffMember(STAFF_EMAIL_PREFIX + UUID.randomUUID() + "@iskoala.com",
                "Trust Lideri", "TRUST_LEAD");
        trustLeadToken = staffAccessToken(staffId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from admin_audit_entries");
        jdbc.update("delete from notifications where recipient_id = ?", userId);
        jdbc.update("delete from user_profile_sync_outbox where user_id = ?", userId);
        jdbc.update("delete from user_badges where user_id = ?", userId);
        for (UUID reviewId : reviewIds) {
            jdbc.update("delete from user_verifications where id = ?", reviewId);
        }
        for (UUID mediaId : mediaIds) {
            jdbc.update("delete from media where id = ?", mediaId);
        }
        jdbc.update("delete from users where id = ?", userId);
        jdbc.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like ?)", STAFF_EMAIL_PREFIX + "%");
        jdbc.update("delete from staff_members where email like ?", STAFF_EMAIL_PREFIX + "%");
    }

    @Test
    void aTrustLeadApprovesAPendingReviewThatCarriesAllFourPhotos() throws Exception {
        UUID reviewId = pendingReview(photo("selfie"), photo("front"), photo("back"), photo("together"));

        mockMvc.perform(post("/admin/identity-verifications/" + reviewId + "/approve")
                        .header("Authorization", "Bearer " + trustLeadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedByStaffId").value(staffId.toString()));

        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "select is_verified from user_verifications where id = ?", Boolean.class, reviewId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "select identity_verified from users where id = ?", Boolean.class, userId));
    }

    @Test
    void aLegacyTwoPhotoReviewIsRefusedWithAStableCodeAndStaysPending() throws Exception {
        UUID reviewId = pendingReview(null, photo("front"), photo("back"), null);

        mockMvc.perform(post("/admin/identity-verifications/" + reviewId + "/approve")
                        .header("Authorization", "Bearer " + trustLeadToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_REVIEW_DOCUMENTS_MISSING"))
                .andExpect(jsonPath("$.errorCategory").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Onay için dört fotoğraf da gerekli: yüz, TCKK ön, TCKK arka ve TCKK yüzün altında. "
                                + "Eksik kanıtla onaylanamaz; incelemeyi reddedin, kullanıcı dört fotoğrafı tekrar yüklesin."));

        mockMvc.perform(post("/admin/identity-verifications/" + reviewId + "/approve")
                        .header("Authorization", "Bearer " + trustLeadToken)
                        .header("Accept-Language", "en"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_REVIEW_DOCUMENTS_MISSING"))
                .andExpect(jsonPath("$.message").value(
                        "Approval needs all four photos: selfie, TCKK front, TCKK back and TCKK under the face. "
                                + "Missing evidence cannot be approved; reject the review and ask for a new upload."));

        assertStillPending(reviewId);
    }

    @Test
    void aPendingReviewMissingOnlyTheTogetherPhotoIsRefused() throws Exception {
        UUID reviewId = pendingReview(photo("selfie"), photo("front"), photo("back"), null);

        mockMvc.perform(post("/admin/identity-verifications/" + reviewId + "/approve")
                        .header("Authorization", "Bearer " + trustLeadToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_REVIEW_DOCUMENTS_MISSING"));

        assertStillPending(reviewId);
    }

    @Test
    void aForgedApprovalBodyClaimingFourPhotosIsIgnoredAndTheServerRecordWins() throws Exception {
        UUID reviewId = pendingReview(null, photo("front"), photo("back"), null);
        String forgedBody = """
                {"selfieDocumentId":"%s","frontDocumentId":"%s","backDocumentId":"%s",
                 "togetherDocumentId":"%s","status":"APPROVED","verified":true}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/admin/identity-verifications/" + reviewId + "/approve")
                        .header("Authorization", "Bearer " + trustLeadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_REVIEW_DOCUMENTS_MISSING"));

        assertStillPending(reviewId);
    }

    @Test
    void aLegacyTwoPhotoReviewCanStillBeRejected() throws Exception {
        UUID reviewId = pendingReview(null, photo("front"), photo("back"), null);

        mockMvc.perform(post("/admin/identity-verifications/" + reviewId + "/reject")
                        .header("Authorization", "Bearer " + trustLeadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Yüz ve birlikte fotoğrafı eksik, dört fotoğrafla yeniden yükleyin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertNotNull(jdbc.queryForObject(
                "select rejected_at from user_verifications where id = ?", Instant.class, reviewId));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "select identity_verified from users where id = ?", Boolean.class, userId));
    }

    @Test
    void aReviewThatIsNoLongerPendingCannotBeApprovedEvenWithFourPhotos() throws Exception {
        UUID reviewId = pendingReview(photo("selfie"), photo("front"), photo("back"), photo("together"));
        jdbc.update("update user_verifications set rejected_at = now(), rejection_reason = 'önce reddedildi' "
                + "where id = ?", reviewId);

        mockMvc.perform(post("/admin/identity-verifications/" + reviewId + "/approve")
                        .header("Authorization", "Bearer " + trustLeadToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_REVIEW_ALREADY_DECIDED"));

        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "select is_verified from user_verifications where id = ?", Boolean.class, reviewId));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "select identity_verified from users where id = ?", Boolean.class, userId));
    }

    @Test
    void anApprovalThatWaitsBehindAConcurrentRejectionFailsClosed() throws Exception {
        UUID reviewId = pendingReview(photo("selfie"), photo("front"), photo("back"), photo("together"));
        StaffPrincipal actor = new StaffPrincipal(staffId, "trust@iskoala.com", "Trust Lideri",
                Set.of("TRUST_LEAD"), Set.of(StaffPermission.IDENTITY_REVIEW), false);
        CountDownLatch rejectionHoldsTheRow = new CountDownLatch(1);
        CountDownLatch releaseRejection = new CountDownLatch(1);
        CountDownLatch approvalStarted = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var rejection = executor.submit(() -> transactions.executeWithoutResult(status -> {
                IdentityVerification row = identityVerificationDao.findByIdForUpdate(reviewId).orElseThrow();
                row.setRejectedAt(Instant.now());
                row.setRejectionReason("Eşzamanlı ret");
                identityVerificationDao.save(row);
                rejectionHoldsTheRow.countDown();
                await(releaseRejection);
            }));
            assertTrue(rejectionHoldsTheRow.await(10, TimeUnit.SECONDS));

            var approval = executor.submit(() -> {
                approvalStarted.countDown();
                return identityDocumentReviewService.approve(actor, reviewId);
            });
            assertTrue(approvalStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> approval.get(250, TimeUnit.MILLISECONDS));

            releaseRejection.countDown();
            rejection.get(10, TimeUnit.SECONDS);

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> approval.get(10, TimeUnit.SECONDS));
            ConflictException conflict = assertInstanceOf(ConflictException.class, failure.getCause());
            assertEquals("IDENTITY_REVIEW_ALREADY_DECIDED", conflict.getErrorCode());
        } finally {
            releaseRejection.countDown();
        }

        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "select is_verified from user_verifications where id = ?", Boolean.class, reviewId));
        assertNotNull(jdbc.queryForObject(
                "select rejected_at from user_verifications where id = ?", Instant.class, reviewId));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "select identity_verified from users where id = ?", Boolean.class, userId));
    }

    private void assertStillPending(UUID reviewId) {
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "select is_verified from user_verifications where id = ?", Boolean.class, reviewId));
        assertNull(jdbc.queryForObject(
                "select rejected_at from user_verifications where id = ?", Instant.class, reviewId));
        assertFalse(jdbc.queryForObject(
                "select identity_verified from users where id = ?", Boolean.class, userId));
    }

    private UUID photo(String slot) {
        UUID mediaId = UUID.randomUUID();
        mediaIds.add(mediaId);
        jdbc.update("insert into media (id, media_type, created_at, is_deleted, file_name, file_type, "
                        + "file_url, file_size) values (?, 'VERIFICATION_DOCUMENT', now(), false, ?, 'image/jpeg', ?, 3)",
                mediaId, slot + ".jpg", "images/" + slot + "-" + mediaId + ".jpg");
        return mediaId;
    }

    private UUID pendingReview(UUID selfieId, UUID frontId, UUID backId, UUID togetherId) {
        UUID reviewId = UUID.randomUUID();
        reviewIds.add(reviewId);
        jdbc.update("insert into user_verifications (id, verification_type, created_at, is_deleted, is_verified, "
                        + "user_id, tc_identity_number, first_name, last_name, birth_date, tc_document_number, "
                        + "selfie_document_id, verification_document_id, back_document_id, together_document_id) "
                        + "values (?, 'IDENTITY', now(), false, false, ?, ?, 'kimlik', 'aday', date '1998-04-12', "
                        + "'A01A00000', ?, ?, ?, ?)",
                reviewId, userId, uniqueTckn(), selfieId, frontId, backId, togetherId);
        return reviewId;
    }

    private static String uniqueTckn() {
        long digits = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 8_999_999_999L) + 1_000_000_000L;
        return "1" + digits;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("identity review lock test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("identity review lock test interrupted", exception);
        }
    }
}
