package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicProfileIT extends AbstractIntegrationTest {

    private static final UUID GEZGIN_SILVER = UUID.fromString("9a1c0f10-0002-4c00-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID completedAssignmentId = UUID.randomUUID();
    private final UUID cancelledAssignmentId = UUID.randomUUID();
    private final UUID summaryId = UUID.randomUUID();
    private final UUID userBadgeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(viewerId);
        insertUser(workerId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Profil", "PROFILE_" + categoryId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, headcount, "
                        + "start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 2, now() - interval '2 days', "
                        + "now() - interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                jobId, viewerId, categoryId, "Profil testi " + jobId);
        insertAssignment(completedAssignmentId, "COMPLETED", "RELEASED_TO_WORKER");
        insertAssignment(cancelledAssignmentId, "CANCELLED", "REFUNDED");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from user_profile_sync_outbox where user_id in (?, ?)", viewerId, workerId);
        jdbc.update("delete from user_badges where id = ?", userBadgeId);
        jdbc.update("delete from rating_summaries where id = ?", summaryId);
        jdbc.update("delete from job_assignments where id in (?, ?)", completedAssignmentId, cancelledAssignmentId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from authorities where user_id in (?, ?)", viewerId, workerId);
        jdbc.update("delete from users where id in (?, ?)", viewerId, workerId);
    }

    @Test
    void aFreshProfileReportsZeroWorkNoRatingAndNoBadges() throws Exception {
        mockMvc.perform(get("/users/{userId}", viewerId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(viewerId.toString()))
                .andExpect(jsonPath("$.data.completedJobCount").value(0))
                .andExpect(jsonPath("$.data.averageRating").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.badges", hasSize(0)));
    }

    @Test
    void theProfileCarriesCompletedWorkTheRatingSummaryAndTheBadges() throws Exception {
        jdbc.update("insert into rating_summaries (id, created_at, is_deleted, user_id, review_count, rating_sum, "
                        + "average_rating) values (?, now(), false, ?, 3, 13, 4.33)",
                summaryId, workerId);
        jdbc.update("insert into user_badges (id, created_at, is_deleted, user_id, badge_id, earned_at) "
                        + "values (?, now(), false, ?, ?, now())",
                userBadgeId, workerId, GEZGIN_SILVER);

        mockMvc.perform(get("/users/{userId}", workerId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedJobCount").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(4.33))
                .andExpect(jsonPath("$.data.reviewCount").value(3))
                .andExpect(jsonPath("$.data.badges", hasSize(1)))
                .andExpect(jsonPath("$.data.badges[0].code").value("GEZGIN_SILVER"))
                .andExpect(jsonPath("$.data.badges[0].earnedAt").isNotEmpty());

        mockMvc.perform(get("/users/{userId}/rating-summary", workerId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(3))
                .andExpect(jsonPath("$.data.averageRating").value(4.33));
        mockMvc.perform(get("/users/{userId}/badges", workerId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("GEZGIN_SILVER"));
    }

    @Test
    void theOwnProfileReportsZeroRatingLikeThePublicProfileWhenNobodyReviewedYet() throws Exception {
        mockMvc.perform(get("/users/me").with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(workerId.toString()))
                .andExpect(jsonPath("$.data.averageRating").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
        mockMvc.perform(get("/users/{userId}/employer-stats", workerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
    }

    @Test
    void aSummaryWithoutAnAverageIsZeroOnEveryProfileShapeAndRawOnTheRatingSummary() throws Exception {
        jdbc.update("insert into rating_summaries (id, created_at, is_deleted, user_id, review_count, rating_sum, "
                        + "average_rating) values (?, now(), false, ?, 2, 8, null)",
                summaryId, workerId);

        mockMvc.perform(get("/users/me").with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
        mockMvc.perform(get("/users/{userId}", workerId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
        mockMvc.perform(get("/users/{userId}/employer-stats", workerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(0))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
        mockMvc.perform(get("/users/{userId}/rating-summary", workerId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.averageRating").value(nullValue()));
    }

    @Test
    void theProfileUpdateAnswerCarriesTheSameRatingFigures() throws Exception {
        jdbc.update("insert into rating_summaries (id, created_at, is_deleted, user_id, review_count, rating_sum, "
                        + "average_rating) values (?, now(), false, ?, 3, 13, 4.33)",
                summaryId, workerId);

        mockMvc.perform(patch("/users/me").with(user(principal(workerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"about\":\"Profil testi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(workerId.toString()))
                .andExpect(jsonPath("$.data.user.about").value("Profil testi"))
                .andExpect(jsonPath("$.data.user.averageRating").value(4.33))
                .andExpect(jsonPath("$.data.user.reviewCount").value(3));
    }

    @Test
    void theOwnProfileCarriesTheSameRatingFiguresAsThePublicProfileAndTheSummary() throws Exception {
        jdbc.update("insert into rating_summaries (id, created_at, is_deleted, user_id, review_count, rating_sum, "
                        + "average_rating) values (?, now(), false, ?, 3, 13, 4.33)",
                summaryId, workerId);

        mockMvc.perform(get("/users/me").with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(workerId.toString()))
                .andExpect(jsonPath("$.data.averageRating").value(4.33))
                .andExpect(jsonPath("$.data.reviewCount").value(3));
        mockMvc.perform(get("/users/{userId}", workerId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.33))
                .andExpect(jsonPath("$.data.reviewCount").value(3));
        mockMvc.perform(get("/users/{userId}/rating-summary", workerId).with(user(principal(viewerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.33))
                .andExpect(jsonPath("$.data.reviewCount").value(3));
        mockMvc.perform(get("/users/{userId}/employer-stats", workerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.33))
                .andExpect(jsonPath("$.data.reviewCount").value(3));
    }

    private void insertAssignment(UUID id, String status, String paymentStatus) {
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, assignment_date_time, work_hours, worker_payout, commission_rate, "
                        + "commission_amount, total_charged, pricing_version, currency) "
                        + "values (?, now(), false, ?, ?, ?, ?, now() - interval '2 days', 0, 100.00, "
                        + "0.1000, 10.00, 110.00, 'v1', 'TRY')",
                id, jobId, workerId, status, paymentStatus);
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)",
                id, id + "@example.com");
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
