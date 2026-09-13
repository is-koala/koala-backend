package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReceivedApplicationsIT extends AbstractIntegrationTest {

    private static final String INBOX = "/users/me/received-applications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID employerA = UUID.randomUUID();
    private final UUID officerO = UUID.randomUUID();
    private final UUID formerF = UUID.randomUUID();
    private final UUID employerB = UUID.randomUUID();
    private final UUID officerZ = UUID.randomUUID();
    private final UUID strangerS = UUID.randomUUID();
    private final List<UUID> workers = new ArrayList<>();
    private final List<UUID> pictures = new ArrayList<>();

    private final UUID companyX = UUID.randomUUID();
    private final UUID companyY = UUID.randomUUID();
    private final UUID companyZ = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    private final UUID jobA1 = UUID.randomUUID();
    private final UUID jobC1 = UUID.randomUUID();
    private final UUID jobB1 = UUID.randomUUID();
    private final UUID jobY1 = UUID.randomUUID();
    private final UUID jobZ1 = UUID.randomUUID();
    private final UUID jobF1 = UUID.randomUUID();

    private final UUID tieBase = UUID.randomUUID();
    private final UUID app1 = UUID.randomUUID();
    private final UUID app2 = UUID.randomUUID();
    private final UUID app3 = new UUID(tieBase.getMostSignificantBits(), 0x1000L);
    private final UUID app4 = new UUID(tieBase.getMostSignificantBits(), 0x2000L);
    private final UUID app5 = UUID.randomUUID();
    private final UUID app6 = UUID.randomUUID();
    private final UUID app7 = UUID.randomUUID();
    private final UUID app8 = UUID.randomUUID();
    private final UUID app9 = UUID.randomUUID();

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @BeforeEach
    void setUp() {
        for (UUID id : List.of(employerA, officerO, formerF, employerB, officerZ, strangerS)) {
            insertUser(id, true);
        }
        for (int i = 0; i < 9; i++) {
            UUID workerId = UUID.randomUUID();
            UUID pictureId = UUID.randomUUID();
            workers.add(workerId);
            pictures.add(pictureId);
            insertUser(workerId, false);
            jdbc.update("insert into media (id, created_at, is_deleted, media_type, file_name, file_type, "
                            + "file_url, file_size) values (?, now(), false, 'IMAGE', 'pp.jpg', 'image/jpeg', ?, 128)",
                    pictureId, "profiles/" + pictureId + ".jpg");
            jdbc.update("update users set profile_picture_id = ?, first_name = ?, last_name = 'Aday' where id = ?",
                    pictureId, "Aday" + i, workerId);
        }

        insertCompany(companyX, "Sirket X");
        insertCompany(companyY, "Sirket Y");
        insertCompany(companyZ, "Sirket Z");
        insertContact(companyX, employerA, "ADMIN", false);
        insertContact(companyX, officerO, "STAFF", false);
        insertContact(companyX, formerF, "ADMIN", true);
        insertContact(companyY, employerB, "ADMIN", false);
        insertContact(companyZ, formerF, "ADMIN", true);
        insertContact(companyZ, officerZ, "ADMIN", false);

        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "latitude, longitude, canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, 40.987654, 29.034567, 1, false)",
                addressId, countryId, cityId, districtId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Gelen kutusu", "INBOX_" + categoryId);

        insertJob(jobA1, employerA, null, "Bireysel A1");
        insertJob(jobC1, employerA, companyX, "Sirket X C1");
        insertJob(jobB1, employerB, null, "Bireysel B1");
        insertJob(jobY1, employerB, companyY, "Sirket Y Y1");
        insertJob(jobZ1, formerF, companyZ, "Sirket Z Z1");
        insertJob(jobF1, formerF, null, "Bireysel F1");

        insertApplication(app1, jobA1, workers.get(0), "APPLIED", now.minus(3, ChronoUnit.HOURS), null);
        insertApplication(app2, jobA1, workers.get(1), "REJECTED", now.minus(1, ChronoUnit.HOURS),
                now.minus(30, ChronoUnit.MINUTES));
        insertApplication(app3, jobC1, workers.get(2), "APPLIED", now.minus(2, ChronoUnit.HOURS), null);
        insertApplication(app4, jobC1, workers.get(3), "APPLIED", now.minus(2, ChronoUnit.HOURS), null);
        insertApplication(app5, jobC1, workers.get(4), "ACCEPTED", now.minus(4, ChronoUnit.HOURS),
                now.minus(3, ChronoUnit.HOURS));
        insertApplication(app6, jobB1, workers.get(5), "APPLIED", now.minus(1, ChronoUnit.HOURS), null);
        insertApplication(app7, jobY1, workers.get(6), "APPLIED", now.minus(1, ChronoUnit.HOURS), null);
        insertApplication(app8, jobZ1, workers.get(7), "APPLIED", now.minus(1, ChronoUnit.HOURS), null);
        insertApplication(app9, jobF1, workers.get(8), "APPLIED", now.minus(1, ChronoUnit.HOURS), null);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from job_applications where job_id in (?, ?, ?, ?, ?, ?)",
                jobA1, jobC1, jobB1, jobY1, jobZ1, jobF1);
        jdbc.update("delete from jobs where id in (?, ?, ?, ?, ?, ?)", jobA1, jobC1, jobB1, jobY1, jobZ1, jobF1);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", addressId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from company_contacts where company_id in (?, ?, ?)", companyX, companyY, companyZ);
        jdbc.update("delete from companies where id in (?, ?, ?)", companyX, companyY, companyZ);
        List<UUID> everyone = new ArrayList<>(List.of(employerA, officerO, formerF, employerB, officerZ, strangerS));
        everyone.addAll(workers);
        String placeholders = String.join(", ", java.util.Collections.nCopies(everyone.size(), "?"));
        Object[] ids = everyone.toArray();
        jdbc.update("delete from authorities where user_id in (" + placeholders + ")", ids);
        jdbc.update("delete from users where id in (" + placeholders + ")", ids);
        String picturePlaceholders = String.join(", ", java.util.Collections.nCopies(pictures.size(), "?"));
        jdbc.update("delete from media where id in (" + picturePlaceholders + ")", pictures.toArray());
    }

    @Test
    void theCreatorSeesEveryApplicationOnHisIndividualAndCompanyJobsAndNothingElse() throws Exception {
        assertEquals(Set.of(app1, app2, app3, app4, app5), Set.copyOf(idsSeenBy(employerA, INBOX)));
    }

    @Test
    void aCompanyOfficerWhoOpenedNoJobStillSeesTheCompanysApplications() throws Exception {
        assertEquals(Set.of(app3, app4, app5), Set.copyOf(idsSeenBy(officerO, INBOX)));
    }

    @Test
    void aFormerContactLosesTheCompanyJobHeOpenedAndKeepsHisIndividualJobs() throws Exception {
        assertEquals(Set.of(app9), Set.copyOf(idsSeenBy(formerF, INBOX)));
        assertEmptyPage(inboxAs(formerF, INBOX + "?jobId=" + jobZ1));
        assertEmptyPage(inboxAs(formerF, INBOX + "?companyId=" + companyZ));
        mockMvc.perform(get("/jobs/" + jobZ1 + "/applications").with(user(principal(formerF))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void theCompanysCurrentContactSeesTheJobAFormerContactOpened() throws Exception {
        assertEquals(Set.of(app8), Set.copyOf(idsSeenBy(officerZ, INBOX)));
    }

    @Test
    void aStrangerSeesNothing() throws Exception {
        assertEmptyPage(inboxAs(strangerS, INBOX));
    }

    @Test
    void anotherEmployerSeesOnlyHisOwnInbox() throws Exception {
        assertEquals(Set.of(app6, app7), Set.copyOf(idsSeenBy(employerB, INBOX)));
    }

    @Test
    void theStatusFilterNarrowsWithinTheScope() throws Exception {
        assertEquals(List.of(app4, app3, app1), idsSeenBy(employerA, INBOX + "?status=APPLIED"));
        assertEquals(List.of(app5), idsSeenBy(officerO, INBOX + "?status=ACCEPTED"));
        assertEmptyPage(inboxAs(strangerS, INBOX + "?status=APPLIED"));
    }

    @Test
    void theJobFilterNarrowsWithinTheScope() throws Exception {
        assertEquals(List.of(app4, app3, app5), idsSeenBy(employerA, INBOX + "?jobId=" + jobC1));
        assertEquals(List.of(app1, app2), idsSeenBy(employerA, INBOX + "?jobId=" + jobA1));
    }

    @Test
    void aJobOutsideTheScopeIsIndistinguishableFromAJobThatDoesNotExist() throws Exception {
        JsonNode foreign = body(inboxAs(employerA, INBOX + "?jobId=" + jobB1).andExpect(status().isOk()));
        JsonNode unknown = body(inboxAs(employerA, INBOX + "?jobId=" + UUID.randomUUID())
                .andExpect(status().isOk()));

        assertEquals(comparable(foreign), comparable(unknown));
        assertEquals(0, foreign.path("data").path("totalElements").asInt());
    }

    @Test
    void theCompanyFilterNarrowsWithinTheScope() throws Exception {
        assertEquals(List.of(app4, app3, app5), idsSeenBy(employerA, INBOX + "?companyId=" + companyX));
        assertEquals(List.of(app4, app3, app5), idsSeenBy(officerO, INBOX + "?companyId=" + companyX));
    }

    @Test
    void aCompanyOutsideTheScopeIsIndistinguishableFromACompanyThatDoesNotExist() throws Exception {
        JsonNode foreign = body(inboxAs(employerA, INBOX + "?companyId=" + companyY)
                .andExpect(status().isOk()));
        JsonNode unknown = body(inboxAs(employerA, INBOX + "?companyId=" + UUID.randomUUID())
                .andExpect(status().isOk()));

        assertEquals(comparable(foreign), comparable(unknown));
        assertEquals(0, foreign.path("data").path("totalElements").asInt());
    }

    @Test
    void filtersCombine() throws Exception {
        assertEquals(List.of(app5), idsSeenBy(employerA,
                INBOX + "?companyId=" + companyX + "&status=ACCEPTED&jobId=" + jobC1));
    }

    @Test
    void pendingApplicationsComeFirstNewestFirstWithTheIdAsTieBreaker() throws Exception {
        assertEquals(List.of(app4, app3, app1, app2, app5), idsSeenBy(employerA, INBOX));
    }

    @Test
    void aClientSortReplacesTheDefaultOrderAndStaysStable() throws Exception {
        assertEquals(List.of(app5, app1, app4, app3, app2),
                idsSeenBy(employerA, INBOX + "?sort=applicationDateTime,asc"));
        assertEquals(List.of(app2, app4, app3, app1, app5),
                idsSeenBy(employerA, INBOX + "?sort=applicationDateTime,desc"));
    }

    @Test
    void anUnknownSortFieldIsRejectedWith400() throws Exception {
        inboxAs(employerA, INBOX + "?sort=notes,asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAGE_SORT_FIELD_NOT_ALLOWED"));
    }

    @Test
    void pagesCarryTheirMetadataAndSplitTheDefaultOrder() throws Exception {
        inboxAs(employerA, INBOX + "?page=0&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(app4.toString()))
                .andExpect(jsonPath("$.data.content[1].id").value(app3.toString()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));

        inboxAs(employerA, INBOX + "?page=2&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(app5.toString()))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void eachRowCarriesTheIdsTheReviewScreenDeepLinksWith() throws Exception {
        inboxAs(employerA, INBOX + "?jobId=" + jobC1 + "&status=APPLIED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(app4.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.content[0].applicationDateTime").exists())
                .andExpect(jsonPath("$.data.content[0].answeredDateTime").value((Object) null))
                .andExpect(jsonPath("$.data.content[0].jobId").value(jobC1.toString()))
                .andExpect(jsonPath("$.data.content[0].jobTitle").value("Sirket X C1"))
                .andExpect(jsonPath("$.data.content[0].jobStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.content[0].companyId").value(companyX.toString()))
                .andExpect(jsonPath("$.data.content[0].companyName").value("Sirket X"))
                .andExpect(jsonPath("$.data.content[0].assignmentId").value((Object) null))
                .andExpect(jsonPath("$.data.content[0].applicant.id").value(workers.get(3).toString()))
                .andExpect(jsonPath("$.data.content[0].applicant.firstName").value("Aday3"))
                .andExpect(jsonPath("$.data.content[0].applicant.profilePictureUrl").isString())
                .andExpect(jsonPath("$.data.content[0].applicant.email").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].applicant.phoneNumber").doesNotExist());

        inboxAs(employerA, INBOX + "?jobId=" + jobA1 + "&status=REJECTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(app2.toString()))
                .andExpect(jsonPath("$.data.content[0].companyId").value((Object) null))
                .andExpect(jsonPath("$.data.content[0].companyName").value((Object) null))
                .andExpect(jsonPath("$.data.content[0].answeredDateTime").exists());
    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheNumberOfRows() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            inboxAs(employerA, INBOX + "?size=1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1));
            long oneRow = statistics.getPrepareStatementCount();

            statistics.clear();
            inboxAs(employerA, INBOX + "?size=5").andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(5))
                    .andExpect(jsonPath("$.data.content[*].applicant.profilePictureUrl").isArray());
            long fiveRows = statistics.getPrepareStatementCount();

            assertEquals(oneRow, fiveRows,
                    "bir satırlık sayfa " + oneRow + " sorgu, beş satırlık sayfa " + fiveRows + " sorgu attı");
            assertTrue(fiveRows <= 4, "beş satırlık sayfa " + fiveRows + " sorgu attı; en fazla dört beklenir");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private List<UUID> idsSeenBy(UUID actorId, String url) throws Exception {
        JsonNode content = body(inboxAs(actorId, url).andExpect(status().isOk())).path("data").path("content");
        List<UUID> ids = new ArrayList<>();
        content.forEach(row -> ids.add(UUID.fromString(row.path("id").asText())));
        return ids;
    }

    private void assertEmptyPage(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private ResultActions inboxAs(UUID actorId, String url) throws Exception {
        return mockMvc.perform(get(url).with(user(principal(actorId))));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode comparable(JsonNode envelope) {
        com.fasterxml.jackson.databind.node.ObjectNode copy = envelope.deepCopy();
        copy.remove("timeStamp");
        copy.remove("requestId");
        return copy;
    }

    private void insertJob(UUID id, UUID creatorId, UUID companyId, String title) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_id, address_public, type, "
                        + "salary_type, salary, min_age, max_age, experience_required, insurance_required, "
                        + "status, headcount, start_date_time, end_date_time, pricing_version, currency, "
                        + "worker_payout, commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, 'Açıklama', 'Pozisyon', ?, false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 1, now(), "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                id, creatorId, companyId, categoryId, companyId == null ? "INDIVIDUAL" : "COMPANY",
                title, addressId);
    }

    private void insertApplication(UUID id, UUID jobId, UUID applicantId, String status,
                                   Instant appliedAt, Instant answeredAt) {
        jdbc.update("insert into job_applications (id, created_at, is_deleted, job_id, user_id, status, "
                        + "application_date_time, answered_date_time, notes) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, 'merhaba')",
                id, jobId, applicantId, status, java.sql.Timestamp.from(appliedAt),
                answeredAt == null ? null : java.sql.Timestamp.from(answeredAt));
    }

    private void insertCompany(UUID id, String name) {
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                id, name, String.valueOf(Math.abs(id.hashCode())), id + "@example.com");
    }

    private void insertContact(UUID companyId, UUID userId, String role, boolean deleted) {
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), ?, ?, ?, ?)",
                UUID.randomUUID(), deleted, companyId, userId, role);
    }

    private void insertUser(UUID id, boolean employer) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', ?, true, false, false)",
                id, id + "@example.com", employer);
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
