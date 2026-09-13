package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentCallbackIT extends AbstractIntegrationTest {

    private static final String IYZICO_ORIGIN = "https://sandbox-api.iyzipay.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theCallbackIsReachableWithoutAuthenticationBecauseTheProviderCallsIt() throws Exception {
        mockMvc.perform(post("/payments/callback").param("token", "bilinmeyen-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void aCallbackWithoutATokenIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/payments/callback"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theProviderFormPostCarryingTheIyzicoOriginIsRedirectedNotRejectedByCors() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        String token = "tok-" + UUID.randomUUID();
        seedAttempt(jobId, assignmentId, token, "SUCCEEDED");

        try {
            mockMvc.perform(post("/payments/callback")
                            .param("token", token)
                            .header("Origin", IYZICO_ORIGIN)
                            .contentType("application/x-www-form-urlencoded"))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(
                            "koala-staging://payment-result?status=success&assignmentId=" + assignmentId));
        } finally {
            cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void anUnknownTokenBehindTheIyzicoOriginStillReachesTheHandlerInsteadOfTheCorsFilter() throws Exception {
        mockMvc.perform(post("/payments/callback")
                        .param("token", "bilinmeyen-token")
                        .header("Origin", IYZICO_ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void aSecondPostAfterSuccessRedirectsToTheSameResultWithoutAskingTheProvider() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        String token = "tok-" + UUID.randomUUID();
        seedAttempt(jobId, assignmentId, token, "SUCCEEDED");
        String expected = "koala-staging://payment-result?status=success&assignmentId=" + assignmentId;

        try {
            mockMvc.perform(post("/payments/callback").param("token", token).header("Origin", IYZICO_ORIGIN))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(expected));
            mockMvc.perform(post("/payments/callback").param("token", token).header("Origin", IYZICO_ORIGIN))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(expected));
            mockMvc.perform(get("/payments/callback").param("token", token))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(expected));

            assertEquals("SUCCEEDED", jdbcTemplate.queryForObject(
                    "select status from payments where provider_token = ?", String.class, token));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "select verification_attempts from payments where provider_token = ?", Integer.class, token),
                    "sağlayıcıya gidilmiş olsaydı doğrulama sayacı artardı");
        } finally {
            cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void theGetReplayIsPublicAndReflectsTheRecordedOutcomeWithoutConsumingTheToken() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        String token = "tok-" + UUID.randomUUID();
        seedAttempt(jobId, assignmentId, token, "FAILED");

        try {
            mockMvc.perform(get("/payments/callback").param("token", token).header("Origin", IYZICO_ORIGIN))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(
                            "koala-staging://payment-result?status=failed&assignmentId=" + assignmentId));
            mockMvc.perform(get("/payments/callback").param("token", token))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(
                            "koala-staging://payment-result?status=failed&assignmentId=" + assignmentId));

            assertEquals("FAILED", jdbcTemplate.queryForObject(
                    "select status from payments where provider_token = ?", String.class, token));
        } finally {
            cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void aPendingAttemptReplayedOverGetIsNotReconciledAndDoesNotReachTheProvider() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        String token = "tok-" + UUID.randomUUID();
        seedAttempt(jobId, assignmentId, token, "VERIFY_PENDING");

        try {
            mockMvc.perform(get("/payments/callback").param("token", token))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(
                            "koala-staging://payment-result?status=failed&assignmentId=" + assignmentId));

            assertEquals("VERIFY_PENDING", jdbcTemplate.queryForObject(
                    "select status from payments where provider_token = ?", String.class, token));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "select verification_attempts from payments where provider_token = ?", Integer.class, token));
        } finally {
            cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void anUnknownTokenOverGetIsRedirectedAsFailedInsteadOfAFourOhFourOrAFourOhOne() throws Exception {
        mockMvc.perform(get("/payments/callback").param("token", "bilinmeyen-token"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("koala-staging://payment-result?status=failed"));
    }

    @Test
    void aGetWithoutATokenIsABadRequest() throws Exception {
        mockMvc.perform(get("/payments/callback"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingPaymentAttemptsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/job-assignments/" + java.util.UUID.randomUUID() + "/payments"))
                .andExpect(status().isUnauthorized());
    }

    private void seedAttempt(UUID jobId, UUID assignmentId, String token, String status) {
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, 'Callback replay', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() + interval '1 day', now() + interval '2 days')
                """, jobId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                     pricing_version, currency,
                     worker_payout, commission_rate, commission_amount, total_charged, work_hours)
                values (?, now(), false, ?, '00000000-0000-0000-0000-000000000001',
                        'CONFIRMED', 'PENDING_COLLECTION', '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00, 0)
                """, assignmentId, jobId);
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id,
                                      provider_token, status, currency, verification_attempts)
                values (?, now(), false, ?, ?, ?, ?, 'TRY', 0)
                """, UUID.randomUUID(), assignmentId, UUID.randomUUID().toString(), token, status);
    }

    private void cleanUp(UUID jobId, UUID assignmentId) {
        jdbcTemplate.update("delete from payments where job_assignment_id = ?", assignmentId);
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
    }
}
