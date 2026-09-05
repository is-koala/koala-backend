package com.exskylab.koala.dataAccess;

import com.exskylab.koala.core.qa.QaMarketplaceSeeder;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "qa.marketplace-seed.version=istanbul-v1",
        "qa.marketplace-seed.worker-email=qa-worker@example.test",
        "qa.marketplace-seed.worker-password=QaWorkerPass!2026",
        "qa.marketplace-seed.employer-email=qa-employer@example.test",
        "qa.marketplace-seed.employer-password=QaEmployerPass!2026",
        "app.api-url=http://localhost:8080",
        "rate-limit.enabled=false"
})
class QaMarketplaceSeedIT extends AbstractIntegrationTest {

    private static final String WORKER_EMAIL = "qa-worker@example.test";
    private static final String WORKER_PASSWORD = "QaWorkerPass!2026";
    private static final String EMPLOYER_EMAIL = "qa-employer@example.test";
    private static final String EMPLOYER_PASSWORD = "QaEmployerPass!2026";

    @Autowired
    private QaMarketplaceSeeder seeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsARealisticIstanbulMarketplaceInTheRealTables() {
        assertEquals(150, count("""
                select count(*) from users
                where email like '%@qa.iskoala.invalid'
                   or email in ('qa-worker@example.test', 'qa-employer@example.test')
                """));
        assertEquals(30, count("select count(*) from companies where email like '%@qa.iskoala.invalid'"));
        assertEquals(180, count("""
                select count(*) from jobs j
                join users u on u.id = j.creator_id
                where u.email like '%@qa.iskoala.invalid' or u.email = 'qa-employer@example.test'
                """));
        assertTrue(count("select count(*) from job_applications") >= 240);
        assertTrue(count("select count(*) from conversations") >= 30);
        assertTrue(count("select count(*) from messages") >= 90);
        assertTrue(count("select count(*) from reviews") >= 20);
        assertEquals(30, count("""
                select count(distinct c.id) from companies c
                join company_contacts cc on cc.company_id = c.id and cc.is_deleted = false
                join users u on u.id = cc.user_id
                where c.email like '%@qa.iskoala.invalid' and u.is_employer = true
                """));

        assertEquals(
                Set.of("OPEN", "CLOSED", "IN_PROGRESS", "CANCELLED", "COMPLETED"),
                Set.copyOf(jdbcTemplate.queryForList("""
                        select distinct j.status from jobs j
                        join users u on u.id = j.creator_id
                        where u.email like '%@qa.iskoala.invalid'
                           or u.email = 'qa-employer@example.test'
                        """, String.class)));

        assertEquals(
                Set.of("PENDING_CONFIRMATION", "CONFIRMED", "IN_PROGRESS", "COMPLETED", "REJECTED", "CANCELLED"),
                Set.copyOf(jdbcTemplate.queryForList(
                        "select distinct status from job_assignments", String.class)));

        assertEquals(
                Set.of("PENDING_COLLECTION", "HELD_IN_ESCROW", "RELEASED_TO_WORKER", "FAILED", "REFUNDED"),
                Set.copyOf(jdbcTemplate.queryForList(
                        "select distinct payment_status from job_assignments", String.class)));
    }

    @Test
    void appliesTheVersionOnceAndNeverOverwritesManualProgress() {
        UUID jobId = jdbcTemplate.queryForObject("""
                select j.id from jobs j
                join users u on u.id = j.creator_id
                where u.email like '%@qa.iskoala.invalid'
                  and j.status = 'OPEN'
                order by j.id limit 1
                """, UUID.class);
        jdbcTemplate.update("update jobs set status = 'CLOSED' where id = ?", jobId);

        boolean applied = seeder.seed(credentials());

        assertFalse(applied);
        assertEquals("CLOSED", jdbcTemplate.queryForObject(
                "select status from jobs where id = ?", String.class, jobId));
        assertEquals(1, count("select count(*) from qa_marketplace_seed_versions where version = 'istanbul-v1'"));
    }

    @Test
    void keepsProviderProofOutOfTheSeededStateFixtures() {
        assertEquals(0, count("""
                select count(*) from users
                where (email like '%@qa.iskoala.invalid'
                    or email in ('qa-worker@example.test', 'qa-employer@example.test'))
                  and iyzico_submerchant_key is not null
                """));
        assertEquals(0, count("""
                select count(*) from companies
                where email like '%@qa.iskoala.invalid'
                  and iyzico_submerchant_key is not null
                """));
        assertEquals(0, count("""
                select count(*) from payments
                where conversation_id like 'qa-state-fixture-%'
                  and (provider_token is not null
                    or provider_payment_id is not null
                    or provider_transaction_id is not null)
                """));
    }

    @Test
    void keepsControlledQaAccountsOutOfSyntheticFinancialState() {
        assertEquals(0, count("""
                select count(*) from job_assignments ja
                join users worker on worker.id = ja.worker_id
                where worker.email = 'qa-worker@example.test'
                """));
        assertEquals(0, count("""
                select count(*) from job_assignments ja
                join jobs j on j.id = ja.job_id
                join users employer on employer.id = j.creator_id
                where employer.email = 'qa-employer@example.test'
                """));
        assertEquals(0, count("""
                select count(*) from payments p
                join job_assignments ja on ja.id = p.job_assignment_id
                join jobs j on j.id = ja.job_id
                join users employer on employer.id = j.creator_id
                where employer.email = 'qa-employer@example.test'
                """));
    }

    @Test
    void usesTheConfiguredPublicOriginForSeededMedia() {
        assertEquals(0, count("""
                select count(*) from media
                where file_name like 'calisan-%'
                  and file_url not like 'http://localhost:8080/qa-assets/%'
                """));
    }

    @Test
    void bothQaAccountsCanLogInThroughThePublicApi() throws Exception {
        assertLoginWorks(WORKER_EMAIL, WORKER_PASSWORD);
        assertLoginWorks(EMPLOYER_EMAIL, EMPLOYER_PASSWORD);
    }

    private QaMarketplaceSeeder.Credentials credentials() {
        return new QaMarketplaceSeeder.Credentials(
                QaMarketplaceSeeder.DATASET_VERSION,
                WORKER_EMAIL,
                WORKER_PASSWORD,
                EMPLOYER_EMAIL,
                EMPLOYER_PASSWORD,
                "http://localhost:8080");
    }

    private void assertLoginWorks(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .header("X-Device-Id", "qa-seed-it-" + email)
                        .header("User-Agent", "Koala QA Seed IT")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
