package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.core.dtos.transport.request.SeatBoardingDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleBoardingDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingEventDto;
import com.exskylab.koala.core.helpers.BoardingTokens;
import com.exskylab.koala.core.properties.TransportBoardingProperties;
import com.exskylab.koala.core.properties.TransportReminderProperties;
import com.exskylab.koala.core.schedulers.TransportReminderScheduler;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import com.exskylab.koala.support.OverlappingWrites;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransportBoardingIT extends AbstractStaffIntegrationTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final String NEAR_LAT = "41.048100";
    private static final String NEAR_LNG = "29.009100";
    private static final String FAR_LAT = "41.060000";
    private static final String FAR_LNG = "29.030000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransportReminderScheduler reminderScheduler;

    @Autowired
    private TransportReminderProperties reminderProperties;

    @Autowired
    private TransportBoardingProperties boardingProperties;

    @Autowired
    private TransportService transportService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final UUID employerId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID formerContactId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID secondWorkerId = UUID.randomUUID();
    private final UUID thirdWorkerId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final List<UUID> users = List.of(employerId, contactId, formerContactId, workerId, secondWorkerId,
            thirdWorkerId, outsiderId);
    private final UUID companyId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID jobAddressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID secondAssignmentId = UUID.randomUUID();
    private final UUID thirdAssignmentId = UUID.randomUUID();
    private final Instant departure = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.SECONDS);
    private final Instant returnDeparture = departure.plus(Duration.ofHours(9));

    private UUID shuttleA;
    private UUID shuttleB;
    private UUID returnShuttle;

    @BeforeEach
    void setUp() throws Exception {
        insertUser(employerId, "İşveren", "Bir");
        insertUser(contactId, "Yetkili", "İki");
        insertUser(formerContactId, "Eski", "Yetkili");
        insertUser(workerId, "Ayşe", "Binen");
        insertUser(secondWorkerId, "Mehmet", "Gecikmiş");
        insertUser(thirdWorkerId, "Zeynep", "İptal");
        insertUser(outsiderId, "Dış", "Kişi");
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Servis Şirketi " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                companyId + "@example.com");
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                + "values (?, now(), false, ?, ?, 'ADMIN')", UUID.randomUUID(), companyId, contactId);
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, deleted_at, company_id, user_id, role) "
                + "values (?, now(), true, now(), ?, ?, 'ADMIN')", UUID.randomUUID(), companyId, formerContactId);
        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "neighborhood, thoroughfare, building_number, latitude, longitude, canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, ?, ?, ?, 1, false)",
                jobAddressId, countryId, cityId, districtId, "Moda", "Caferağa Sokak", "12", 40.987654, 29.034567);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Biniş", "BOARDING_" + categoryId);
        LocalDateTime jobStart = LocalDateTime.ofInstant(departure.plus(Duration.ofHours(1)), ISTANBUL);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, employer_type, "
                        + "title, description, position, address_id, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, start_date_time, end_date_time) "
                        + "values (?, now(), false, ?, ?, ?, 'COMPANY', ?, 'Açıklama', 'Pozisyon', ?, false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', ?, ?)",
                jobId, employerId, companyId, categoryId, "Biniş testi " + jobId, jobAddressId,
                jobStart, jobStart.plusHours(8));
        insertAssignment(assignmentId, workerId);
        insertAssignment(secondAssignmentId, secondWorkerId);
        insertAssignment(thirdAssignmentId, thirdWorkerId);

        putPlan(employerId, planJson()).andExpect(status().isOk());
        shuttleA = shuttleNamed("A servisi");
        shuttleB = shuttleNamed("B servisi");
        returnShuttle = shuttleNamed("Dönüş servisi");
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?)", workerId, secondWorkerId,
                thirdWorkerId);
    }

    @AfterEach
    void tearDown() {
        narrowTheBoardingInsertWindow();
        List<UUID> stopAddresses = jdbc.queryForList("select st.address_id from shuttle_stops st "
                + "join shuttles s on s.id = st.shuttle_id join transport_plans p on p.id = s.plan_id "
                + "where p.job_id = ?", UUID.class, jobId);
        jdbc.update("delete from boarding_events where job_assignment_id in (select id from job_assignments "
                + "where job_id = ?)", jobId);
        jdbc.update("delete from seat_assignments where job_assignment_id in (select id from job_assignments "
                + "where job_id = ?)", jobId);
        jdbc.update("delete from shuttle_stops where shuttle_id in (select s.id from shuttles s "
                + "join transport_plans p on p.id = s.plan_id where p.job_id = ?)", jobId);
        jdbc.update("delete from shuttles where plan_id in (select id from transport_plans where job_id = ?)", jobId);
        jdbc.update("delete from transport_plans where job_id = ?", jobId);
        stopAddresses.forEach(id -> jdbc.update("delete from addresses where id = ?", id));
        for (UUID id : users) {
            jdbc.update("delete from notifications where recipient_id = ?", id);
            jdbc.update("delete from devices where user_id = ?", id);
        }
        jdbc.update("delete from job_assignments where job_id = ?", jobId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", jobAddressId);
        jdbc.update("delete from addresses where district_id = ?", districtId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        for (UUID id : users) {
            jdbc.update("delete from authorities where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
        deleteStaffMembersLike("boarding-staff-%@iskoala.com");
    }

    @Test
    void anOutboundBoardingBeforeTheJobStartsKeepsItsTimeAndCarriesNoWindowFlag() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        Instant before = Instant.now();
        JsonNode qr = data(mockMvc.perform(get("/shuttles/{id}/qr", shuttleA).with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.static").value(false))
                .andExpect(jsonPath("$.data.uri", startsWith("koala://board/shuttle/" + shuttleA + "?t=s1."))));
        String token = qr.get("token").asText();
        assertEquals("koala://board/shuttle/" + shuttleA + "?t=" + token, qr.get("uri").asText());
        Instant refreshAt = Instant.parse(qr.get("refreshAt").asText());
        Instant expiresAt = Instant.parse(qr.get("expiresAt").asText());
        assertTrue(refreshAt.isAfter(before), "the screen must refresh after the token was minted");
        assertEquals(boardingProperties.getTokenTtl(), Duration.between(refreshAt, expiresAt),
                "the token stays accepted one whole window after the screen moves on");

        Instant capturedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        JsonNode event = data(scan(workerId, shuttleA, token, capturedAt, NEAR_LAT, NEAR_LNG, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("BOARDED"))
                .andExpect(jsonPath("$.data.source").value("WORKER_SCANNED_SHUTTLE_QR"))
                .andExpect(jsonPath("$.data.seatAssignmentId").value(seatId.toString()))
                .andExpect(jsonPath("$.data.jobAssignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.shuttleId").value(shuttleA.toString()))
                .andExpect(jsonPath("$.data.recordedById").value(workerId.toString()))
                .andExpect(jsonPath("$.data.flags", hasSize(0))));
        assertEquals(capturedAt, Instant.parse(event.get("occurredAt").asText()),
                "the boarding time must not be clamped to the job window");
        assertEquals(capturedAt, Instant.parse(event.get("capturedAt").asText()));
        assertEquals("", jdbc.queryForObject("select flags from boarding_events where seat_assignment_id = ?",
                String.class, seatId));
    }

    @Test
    void aBoardingFarFromTheStopAndOffItsScheduleIsRecordedWithBothFlags() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        Instant capturedAt = Instant.now().minus(Duration.ofMinutes(50)).truncatedTo(ChronoUnit.MILLIS);

        JsonNode event = data(scan(workerId, shuttleA, shuttleToken(shuttleA, false), capturedAt, FAR_LAT, FAR_LNG,
                UUID.randomUUID())
                .andExpect(status().isOk()));

        assertEquals(Set.of("FAR_FROM_STOP", "OFF_SCHEDULE"), flagsOf(event));
        assertEquals(capturedAt, Instant.parse(event.get("occurredAt").asText()));
    }

    @Test
    void theEmployerScansTheWorkerQrOfEitherDirection() throws Exception {
        UUID outboundSeat = pickSeat(workerId, assignmentId, shuttleA);
        UUID returnSeat = pickSeat(workerId, assignmentId, returnShuttle);

        JsonNode outboundQr = data(mockMvc.perform(get("/job-assignments/{id}/transport/qr", assignmentId)
                        .with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.static").value(false)));
        assertEquals("koala://board/worker/" + outboundSeat + "?t=" + outboundQr.get("token").asText(),
                outboundQr.get("uri").asText());

        JsonNode returnQr = data(mockMvc.perform(get("/job-assignments/{id}/transport/qr", assignmentId)
                        .param("direction", "RETURN")
                        .with(user(principal(workerId))))
                .andExpect(status().isOk()));
        assertEquals("koala://board/worker/" + returnSeat + "?t=" + returnQr.get("token").asText(),
                returnQr.get("uri").asText());

        mark(employerId, returnSeat, "{\"token\":\"" + returnQr.get("token").asText() + "\",\"capturedAt\":\""
                + Instant.now().truncatedTo(ChronoUnit.MILLIS) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("BOARDED"))
                .andExpect(jsonPath("$.data.source").value("EMPLOYER_SCANNED_WORKER_QR"))
                .andExpect(jsonPath("$.data.shuttleId").value(returnShuttle.toString()))
                .andExpect(jsonPath("$.data.recordedById").value(employerId.toString()));
        mark(contactId, outboundSeat, "{\"token\":\"" + outboundQr.get("token").asText() + "\",\"capturedAt\":\""
                + Instant.now().truncatedTo(ChronoUnit.MILLIS) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatAssignmentId").value(outboundSeat.toString()));

        mark(employerId, outboundSeat, "{\"token\":\"" + returnQr.get("token").asText() + "\",\"capturedAt\":\""
                + Instant.now() + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SEAT_ASSIGNMENT_NOT_FOUND"));
        mockMvc.perform(get("/job-assignments/{id}/transport/qr", assignmentId).with(user(principal(employerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_ASSIGNMENT_NOT_FOUND"));
        mockMvc.perform(get("/job-assignments/{id}/transport/qr", secondAssignmentId)
                        .param("direction", "RETURN")
                        .with(user(principal(secondWorkerId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SEAT_NOT_SELECTED"));
    }

    @Test
    void anExpiredTokenIs400AndAnotherShuttlesTokenLooksLikeAMissingShuttle() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        String secret = jdbc.queryForObject("select qr_secret from shuttles where id = ?", String.class, shuttleA);
        String stale = BoardingTokens.mintShuttle(secret, shuttleA, Instant.now().minus(Duration.ofMinutes(25)),
                boardingProperties.getTokenTtl(), false);
        scan(workerId, shuttleA, stale, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_TOKEN_EXPIRED"));

        String tokenOfB = shuttleToken(shuttleB, false);
        UUID phantom = UUID.randomUUID();
        String missing = envelope(scan(workerId, phantom, tokenOfB, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isNotFound()), phantom);
        assertEquals(missing, envelope(scan(workerId, shuttleA, tokenOfB, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isNotFound()), shuttleA));
        assertEquals(missing, envelope(scan(workerId, shuttleB, tokenOfB, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isNotFound()), shuttleB));
        assertEquals(
                envelope(mockMvc.perform(get("/shuttles/{id}/qr", phantom).with(user(principal(employerId))))
                        .andExpect(status().isNotFound()), phantom),
                envelope(mockMvc.perform(get("/shuttles/{id}/qr", shuttleA).with(user(principal(outsiderId))))
                        .andExpect(status().isNotFound()), shuttleA));
        assertEquals(0, boardingRows());
    }

    @Test
    void repeatedScansAndAnEmployerMarkOfABoardedSeatAnswerWithTheSameEvent() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        String token = shuttleToken(shuttleA, false);
        UUID clientEventId = UUID.randomUUID();
        Instant capturedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String eventId = data(scan(workerId, shuttleA, token, capturedAt, NEAR_LAT, NEAR_LNG, clientEventId)
                .andExpect(status().isOk())).get("id").asText();

        scan(workerId, shuttleA, token, capturedAt.plusSeconds(20), NEAR_LAT, NEAR_LNG, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId));
        scan(workerId, shuttleA, token, capturedAt, NEAR_LAT, NEAR_LNG, clientEventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId));
        String workerToken = data(mockMvc.perform(get("/job-assignments/{id}/transport/qr", assignmentId)
                .with(user(principal(workerId)))).andExpect(status().isOk())).get("token").asText();
        mark(employerId, seatId, "{\"token\":\"" + workerToken + "\",\"capturedAt\":\"" + Instant.now() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId))
                .andExpect(jsonPath("$.data.source").value("WORKER_SCANNED_SHUTTLE_QR"));
        mark(contactId, seatId, "{\"kind\":\"BOARDED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId));

        scan(workerId, shuttleA, token, capturedAt, FAR_LAT, FAR_LNG, clientEventId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_BOARDING_IDEMPOTENCY_CONFLICT"));
        mark(employerId, seatId, "{\"kind\":\"NO_SHOW\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_ALREADY_BOARDED"));
        assertEquals(1, boardingRows());
    }

    @Test
    void aRetryIdIsScopedToItsSeat() throws Exception {
        UUID firstSeat = pickSeat(workerId, assignmentId, shuttleA);
        UUID secondSeat = pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        String token = shuttleToken(shuttleA, false);
        UUID sharedRetryId = UUID.randomUUID();

        String first = data(scan(workerId, shuttleA, token, Instant.now(), NEAR_LAT, NEAR_LNG, sharedRetryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatAssignmentId").value(firstSeat.toString()))).get("id").asText();
        String second = data(scan(secondWorkerId, shuttleA, token, Instant.now().plusMillis(7), null, null,
                sharedRetryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatAssignmentId").value(secondSeat.toString()))).get("id").asText();

        assertNotEquals(first, second);
        assertEquals(2, boardingRows());
    }

    @Test
    void aNoShowMarkRepeatsIdempotentlyAndALateBoardingStillWins() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        String noShow = data(mark(employerId, seatId, "{\"kind\":\"NO_SHOW\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("NO_SHOW"))
                .andExpect(jsonPath("$.data.source").value("EMPLOYER_MARKED"))
                .andExpect(jsonPath("$.data.capturedAt").doesNotExist())).get("id").asText();
        mark(contactId, seatId, "{\"kind\":\"NO_SHOW\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(noShow));

        String boarded = data(scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), null, null,
                UUID.randomUUID()).andExpect(status().isOk())).get("id").asText();

        mockMvc.perform(get("/shuttles/{id}/boardings", shuttleA).with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].boarded").value(true))
                .andExpect(jsonPath("$.data[0].event.id").value(boarded))
                .andExpect(jsonPath("$.data[0].event.kind").value("BOARDED"));
        assertEquals(2, boardingRows());
    }

    @Test
    void theBoardingListCarriesLiveSeatsOnlyWithTheirDecidingEvent() throws Exception {
        UUID boardedSeat = pickSeat(workerId, assignmentId, shuttleA);
        UUID waitingSeat = pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        pickSeat(thirdWorkerId, thirdAssignmentId, shuttleA);
        jdbc.update("update job_assignments set status = 'CANCELLED' where id = ?", thirdAssignmentId);
        scan(workerId, shuttleA, shuttleToken(shuttleA, true), Instant.now(), FAR_LAT, FAR_LNG, UUID.randomUUID())
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/shuttles/{id}/boardings", shuttleA).with(user(principal(contactId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode rows = objectMapper.readTree(body).get("data");
        JsonNode boarded = rowOf(rows, boardedSeat);
        JsonNode waiting = rowOf(rows, waitingSeat);
        assertTrue(boarded.get("boarded").asBoolean());
        assertEquals("Ayşe", boarded.get("firstName").asText());
        assertEquals(workerId.toString(), boarded.get("workerId").asText());
        assertEquals(Set.of("FAR_FROM_STOP", "STATIC_QR"), flagsOf(boarded.get("event")));
        assertFalse(waiting.get("boarded").asBoolean());
        assertTrue(waiting.get("event").isNull());
        assertFalse(body.contains(thirdWorkerId.toString()), "a cancelled assignment must not look not-boarded");

        UUID phantom = UUID.randomUUID();
        assertEquals(
                envelope(mockMvc.perform(get("/shuttles/{id}/boardings", phantom).with(user(principal(outsiderId))))
                        .andExpect(status().isNotFound()), phantom),
                envelope(mockMvc.perform(get("/shuttles/{id}/boardings", shuttleA).with(user(principal(outsiderId))))
                        .andExpect(status().isNotFound()), shuttleA));
    }

    @Test
    void theBoardingListIsBuiltWithoutAQueryPerSeat() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, shuttleB);
        mark(employerId, seatOf(assignmentId, "OUTBOUND"), "{\"kind\":\"BOARDED\"}").andExpect(status().isOk());
        mark(employerId, seatOf(secondAssignmentId, "OUTBOUND"), "{\"kind\":\"NO_SHOW\"}").andExpect(status().isOk());

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mockMvc.perform(get("/shuttles/{id}/boardings", shuttleA).with(user(principal(employerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
            long statementsForOneSeat = statistics.getPrepareStatementCount();

            pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
            mark(employerId, seatOf(secondAssignmentId, "OUTBOUND"), "{\"kind\":\"NO_SHOW\"}")
                    .andExpect(status().isOk());
            statistics.clear();
            mockMvc.perform(get("/shuttles/{id}/boardings", shuttleA).with(user(principal(employerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)));

            assertEquals(statementsForOneSeat, statistics.getPrepareStatementCount(),
                    "biniş listesi koltuk sayısıyla büyüyen sorgu atıyor (N+1)");
            assertEquals(0, statistics.getEntityFetchCount(), "biniş listesi koltuk başına lazy entity yüklüyor");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test
    void aStaticQrIsFlaggedAndRotatingTheKeyRevokesEveryEarlierQr() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        mockMvc.perform(get("/shuttles/{id}/qr", shuttleA).param("static", "true").with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.static").value(true))
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.refreshAt").doesNotExist());
        String printed = shuttleToken(shuttleA, true);
        String workerToken = data(mockMvc.perform(get("/job-assignments/{id}/transport/qr", assignmentId)
                .with(user(principal(workerId)))).andExpect(status().isOk())).get("token").asText();
        String secretBefore = jdbc.queryForObject("select qr_secret from shuttles where id = ?", String.class, shuttleA);

        mockMvc.perform(post("/shuttles/{id}/qr/rotate", shuttleA).with(user(principal(outsiderId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SHUTTLE_NOT_FOUND"));
        assertEquals(secretBefore, jdbc.queryForObject("select qr_secret from shuttles where id = ?", String.class,
                shuttleA));

        String fresh = data(mockMvc.perform(post("/shuttles/{id}/qr/rotate", shuttleA)
                        .with(user(principal(contactId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.static").value(false))
                .andExpect(jsonPath("$.data.uri", startsWith("koala://board/shuttle/" + shuttleA)))).get("token")
                .asText();
        assertNotEquals(secretBefore, jdbc.queryForObject("select qr_secret from shuttles where id = ?",
                String.class, shuttleA));

        scan(secondWorkerId, shuttleA, printed, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SHUTTLE_NOT_FOUND"));
        mark(employerId, seatId, "{\"token\":\"" + workerToken + "\",\"capturedAt\":\"" + Instant.now() + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SEAT_ASSIGNMENT_NOT_FOUND"));
        assertEquals(0, boardingRows());
        scan(secondWorkerId, shuttleA, fresh, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flags", hasSize(0)));
    }

    @Test
    void theMarkingRulesAreValidatedAtTheBoundary() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        mark(employerId, seatId, "{\"kind\":\"ALIGHTED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("kindConsistentWithToken"));
        mark(employerId, seatId, "{\"kind\":\"NO_SHOW\",\"token\":\"w1.x.y.z\",\"capturedAt\":\"" + Instant.now()
                + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("kindConsistentWithToken"));
        mark(employerId, seatId, "{\"latitude\":41.05}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("positionComplete"));
        mockMvc.perform(post("/shuttles/{id}/boardings", shuttleA).with(user(principal(workerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"s1.x.y.z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("capturedAt"));
        mark(outsiderId, seatId, "{\"kind\":\"BOARDED\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SEAT_ASSIGNMENT_NOT_FOUND"));
        jdbc.update("update job_assignments set status = 'CANCELLED' where id = ?", assignmentId);
        mark(employerId, seatId, "{\"kind\":\"BOARDED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_ASSIGNMENT_NOT_ACTIVE"));
        assertEquals(0, boardingRows());
    }

    @Test
    void theStaffTimelineKeepsTheBoardingAfterThePlanDropsTheSeat() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        String eventId = data(scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), null, null,
                UUID.randomUUID()).andExpect(status().isOk())).get("id").asText();
        String token = staffTokenFor("boarding-staff-" + UUID.randomUUID() + "@iskoala.com", "Analist",
                "FINANCE_ANALYST");

        mockMvc.perform(get("/admin/job-assignments/{id}", assignmentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline", hasSize(2)))
                .andExpect(jsonPath("$.data.timeline[0].type").value("SEAT"))
                .andExpect(jsonPath("$.data.timeline[0].seatAssignmentId").value(seatId.toString()))
                .andExpect(jsonPath("$.data.timeline[1].type").value("BOARDING"))
                .andExpect(jsonPath("$.data.timeline[1].id").value(eventId))
                .andExpect(jsonPath("$.data.timeline[1].kind").value("BOARDED"))
                .andExpect(jsonPath("$.data.timeline[1].source").value("WORKER_SCANNED_SHUTTLE_QR"))
                .andExpect(jsonPath("$.data.timeline[1].shuttleId").value(shuttleA.toString()));

        putPlan(employerId, "{\"outboundMethod\":\"SELF\",\"returnMethod\":\"NONE\",\"shuttles\":[]}")
                .andExpect(status().isOk());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select is_deleted from seat_assignments where id = ?",
                Boolean.class, seatId));

        mockMvc.perform(get("/admin/job-assignments/{id}", assignmentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline", hasSize(1)))
                .andExpect(jsonPath("$.data.timeline[0].type").value("BOARDING"))
                .andExpect(jsonPath("$.data.timeline[0].id").value(eventId))
                .andExpect(jsonPath("$.data.timeline[0].shuttleId").value(shuttleA.toString()))
                .andExpect(jsonPath("$.data.timeline[0].seatAssignmentId").value(seatId.toString()));
    }

    @Test
    void theBoardingReminderGoesFifteenMinutesBeforeDepartureOnlyToSeatsNotYetSettled() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isOk());
        silenceTheEarlierReminders();
        jdbc.update("delete from notifications where recipient_id in (?, ?)", workerId, secondWorkerId);

        assertEquals(0, reminderScheduler.sendDueReminders(departure.minus(Duration.ofMinutes(16))));
        assertEquals(0, transportNotices(secondWorkerId));

        reminderScheduler.sendDueReminders(departure.minus(Duration.ofMinutes(14)));
        reminderScheduler.sendDueReminders(departure.minus(Duration.ofMinutes(9)));

        assertEquals(1, transportNotices(secondWorkerId));
        assertEquals(0, transportNotices(workerId));
        String body = jdbc.queryForObject("select body from notifications where recipient_id = ? and channel = 'IN_APP' "
                + "and category = 'TRANSPORT'", String.class, secondWorkerId);
        assertTrue(body.contains("QR"), body);
        assertTrue(body.contains("15"), body);
        assertEquals(2, jdbc.queryForObject("select count(*) from seat_assignments where shuttle_id = ? "
                + "and boarding_reminder_sent_at is not null and is_deleted = false", Integer.class, shuttleA));
    }

    @Test
    void theNotBoardedNoticeIsOneMessagePerDepartureToEveryCurrentEditor() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        UUID noShowSeat = pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        pickSeat(thirdWorkerId, thirdAssignmentId, shuttleA);
        List<UUID> lateWorkers = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> lateAssignments = List.of(UUID.randomUUID(), UUID.randomUUID());
        insertUser(lateWorkers.get(0), "Ali", "Gelmeyen");
        insertUser(lateWorkers.get(1), "Veli", "Uyuyan");
        try {
            for (int i = 0; i < lateWorkers.size(); i++) {
                insertAssignment(lateAssignments.get(i), lateWorkers.get(i));
                pickSeat(lateWorkers.get(i), lateAssignments.get(i), shuttleA);
            }
            scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), null, null, UUID.randomUUID())
                    .andExpect(status().isOk());
            mark(employerId, noShowSeat, "{\"kind\":\"NO_SHOW\"}").andExpect(status().isOk());
            jdbc.update("update job_assignments set status = 'CANCELLED' where id = ?", thirdAssignmentId);
            silenceTheEarlierReminders();
            jdbc.update("update seat_assignments set boarding_reminder_sent_at = now() where shuttle_id = ?",
                    shuttleA);
            int configured = reminderProperties.getNotBoardedAfterMinutes();
            reminderProperties.setNotBoardedAfterMinutes(12);
            try {
                reminderScheduler.sendDueReminders(departure.plus(Duration.ofMinutes(11)));
                assertEquals(0, transportNotices(employerId), "the delay has not passed yet");

                reminderScheduler.sendDueReminders(departure.plus(Duration.ofMinutes(13)));
                reminderScheduler.sendDueReminders(departure.plus(Duration.ofMinutes(18)));
            } finally {
                reminderProperties.setNotBoardedAfterMinutes(configured);
            }

            assertEquals(1, transportNotices(employerId), "one notice per departure, not one per missing seat");
            assertEquals(1, transportNotices(contactId), "a current company contact edits the job too");
            assertEquals(0, transportNotices(formerContactId), "a removed contact is no longer an editor");
            String body = jdbc.queryForObject("select body from notifications where recipient_id = ? "
                    + "and channel = 'IN_APP' and category = 'TRANSPORT'", String.class, employerId);
            assertTrue(body.contains("12 dakika"), "the configured delay must be in the text: " + body);
            assertTrue(body.contains("A servisi"), body);
            assertTrue(body.contains(" 2 kişi"), body);
            assertTrue(body.contains("Ali Gelmeyen") && body.contains("Veli Uyuyan"), body);
            assertFalse(body.contains("Ayşe"), "a boarded worker is not missing: " + body);
            assertFalse(body.contains("Mehmet"), "a no-show was already settled by the employer: " + body);
            assertFalse(body.contains("Zeynep"), "a cancelled assignment is not expected on the shuttle: " + body);
            assertEquals("JOB", jdbc.queryForObject("select target_type from notifications where recipient_id = ? "
                    + "and channel = 'IN_APP'", String.class, employerId));
            assertEquals(jobId, jdbc.queryForObject("select target_id from notifications where recipient_id = ? "
                    + "and channel = 'IN_APP'", UUID.class, employerId));
        } finally {
            for (int i = 0; i < lateWorkers.size(); i++) {
                jdbc.update("delete from seat_assignments where job_assignment_id = ?", lateAssignments.get(i));
                jdbc.update("delete from notifications where recipient_id = ?", lateWorkers.get(i));
                jdbc.update("delete from job_assignments where id = ?", lateAssignments.get(i));
                jdbc.update("delete from users where id = ?", lateWorkers.get(i));
            }
        }
    }

    @Test
    void theNotBoardedNoticeIgnoresDeparturesOlderThanTheLookback() throws Exception {
        pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        silenceTheEarlierReminders();
        jdbc.update("update seat_assignments set boarding_reminder_sent_at = now() where shuttle_id = ?", shuttleA);
        Instant lateDeploy = departure.plus(Duration.ofMinutes(reminderProperties.getNotBoardedAfterMinutes()
                + reminderProperties.getNotBoardedLookbackMinutes() + 1));

        reminderScheduler.sendDueReminders(lateDeploy);

        assertEquals(0, transportNotices(employerId));
        assertEquals(0, transportNotices(contactId));
        assertNull(jdbc.queryForObject("select not_boarded_notice_sent_at from seat_assignments "
                + "where job_assignment_id = ? and is_deleted = false", Object.class, secondAssignmentId));
    }

    @Test
    void aWorkerScanAndAnEmployerMarkRacingOnOneSeatLeaveOneBoarding() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        String token = shuttleToken(shuttleA, false);
        widenTheBoardingInsertWindow();
        try {
            OverlappingWrites overlap = new OverlappingWrites();
            List<String> outcomes = overlap.outcomesOf(
                    () -> asUser(workerId, overlap, () -> transportService.boardOnShuttle(shuttleA,
                            new ShuttleBoardingDto(token, Instant.now(), null, null, UUID.randomUUID()))),
                    () -> asUser(employerId, overlap, () -> transportService.markSeatBoarding(seatId,
                            new SeatBoardingDto(null, null, null, null, UUID.randomUUID(), null))));

            assertEquals(2, outcomes.stream().filter(outcome -> outcome.startsWith("EVENT ")).count(),
                    "both writers must get the boarding: " + outcomes);
            assertEquals(outcomes.get(0), outcomes.get(1), "both writers must see one and the same event");
            assertEquals(1, boardingRows());
        } finally {
            narrowTheBoardingInsertWindow();
        }
    }

    private interface Write {
        BoardingEventDto run();
    }

    private String asUser(UUID actorId, OverlappingWrites overlap, Write write) {
        UserPrincipal principal = principal(actorId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        try {
            BoardingEventDto event = transactions.execute(status -> {
                overlap.meet();
                return write.run();
            });
            return "EVENT " + event.getId();
        } catch (RuntimeException failure) {
            return failure.getClass().getSimpleName() + ": " + failure.getMessage();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void widenTheBoardingInsertWindow() {
        jdbc.execute("""
                create or replace function delay_boarding_event_insert() returns trigger as $$
                begin
                    perform pg_sleep(0.5);
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbc.execute("create trigger delay_boarding_event_insert before insert on boarding_events "
                + "for each row execute function delay_boarding_event_insert()");
    }

    private void narrowTheBoardingInsertWindow() {
        jdbc.execute("drop trigger if exists delay_boarding_event_insert on boarding_events");
        jdbc.execute("drop function if exists delay_boarding_event_insert()");
    }

    private void silenceTheEarlierReminders() {
        jdbc.update("update seat_assignments set evening_reminder_sent_at = now(), departure_reminder_sent_at = now() "
                + "where shuttle_id in (?, ?, ?)", shuttleA, shuttleB, returnShuttle);
    }

    private int transportNotices(UUID recipient) {
        return jdbc.queryForObject("select count(*) from notifications where recipient_id = ? and channel = 'IN_APP' "
                + "and category = 'TRANSPORT'", Integer.class, recipient);
    }

    private int boardingRows() {
        return jdbc.queryForObject("select count(*) from boarding_events where job_assignment_id in "
                + "(select id from job_assignments where job_id = ?)", Integer.class, jobId);
    }

    private Set<String> flagsOf(JsonNode event) {
        Set<String> flags = new java.util.TreeSet<>();
        event.get("flags").forEach(flag -> flags.add(flag.asText()));
        return flags;
    }

    private JsonNode rowOf(JsonNode rows, UUID seatId) {
        for (JsonNode row : rows) {
            if (row.get("seatAssignmentId").asText().equals(seatId.toString())) {
                return row;
            }
        }
        throw new AssertionError("no row for seat " + seatId + " in " + rows);
    }

    private String shuttleToken(UUID shuttleId, boolean staticQr) throws Exception {
        return data(mockMvc.perform(get("/shuttles/{id}/qr", shuttleId).param("static", Boolean.toString(staticQr))
                .with(user(principal(employerId)))).andExpect(status().isOk())).get("token").asText();
    }

    private ResultActions scan(UUID actorId, UUID shuttleId, String token, Instant capturedAt, String latitude,
                               String longitude, UUID clientEventId) throws Exception {
        List<String> fields = new ArrayList<>();
        fields.add("\"token\":\"" + token + "\"");
        fields.add("\"capturedAt\":\"" + capturedAt + "\"");
        if (latitude != null) {
            fields.add("\"latitude\":" + new BigDecimal(latitude));
            fields.add("\"longitude\":" + new BigDecimal(longitude));
        }
        if (clientEventId != null) {
            fields.add("\"clientEventId\":\"" + clientEventId + "\"");
        }
        return mockMvc.perform(post("/shuttles/{id}/boardings", shuttleId)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + String.join(",", fields) + "}"));
    }

    private ResultActions mark(UUID actorId, UUID seatId, String body) throws Exception {
        return mockMvc.perform(post("/seat-assignments/{id}/boardings", seatId)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID pickSeat(UUID actorId, UUID assignment, UUID shuttleId) throws Exception {
        UUID stopId = jdbc.queryForObject("select id from shuttle_stops where shuttle_id = ? and is_deleted = false",
                UUID.class, shuttleId);
        mockMvc.perform(put("/job-assignments/{id}/transport/seat", assignment)
                        .with(user(principal(actorId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shuttleId\":\"" + shuttleId + "\",\"stopId\":\"" + stopId + "\"}"))
                .andExpect(status().isOk());
        String direction = jdbc.queryForObject("select direction from shuttles where id = ?", String.class, shuttleId);
        return seatOf(assignment, direction);
    }

    private UUID seatOf(UUID assignment, String direction) {
        return jdbc.queryForObject("select id from seat_assignments where job_assignment_id = ? and direction = ? "
                + "and is_deleted = false", UUID.class, assignment, direction);
    }

    private UUID shuttleNamed(String name) {
        return jdbc.queryForObject("select s.id from shuttles s join transport_plans p on p.id = s.plan_id "
                + "where p.job_id = ? and s.name = ? and s.is_deleted = false", UUID.class, jobId, name);
    }

    private JsonNode data(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    private ResultActions putPlan(UUID actorId, String body) throws Exception {
        return mockMvc.perform(put("/jobs/{jobId}/transport-plan", jobId)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String planJson() {
        return "{\"outboundMethod\":\"SHUTTLE\",\"returnMethod\":\"SHUTTLE\",\"shuttles\":["
                + shuttleJson("OUTBOUND", "A servisi", "34 ABC 12", departure) + ","
                + shuttleJson("OUTBOUND", "B servisi", "34 XYZ 99", departure) + ","
                + shuttleJson("RETURN", "Dönüş servisi", "34 DNS 01", returnDeparture) + "]}";
    }

    private String shuttleJson(String direction, String name, String plate, Instant scheduledAt) {
        return "{\"direction\":\"" + direction + "\",\"name\":\"" + name + "\",\"plate\":\"" + plate + "\","
                + "\"capacity\":6,\"driverName\":\"Ahmet Şoför\",\"driverPhone\":\"+905551112233\","
                + "\"stops\":[{\"label\":\"Kadıköy İskele\",\"scheduledAt\":\"" + scheduledAt + "\",\"address\":"
                + "{\"countryId\":\"" + countryId + "\",\"cityId\":\"" + cityId + "\",\"districtId\":\"" + districtId
                + "\",\"components\":{\"neighborhood\":\"Yıldız Mah.\",\"thoroughfare\":\"Barbaros Bulvarı\","
                + "\"buildingNumber\":\"45\",\"postalCode\":\"34353\"},"
                + "\"position\":{\"latitude\":41.0480,\"longitude\":29.0090,\"source\":\"USER_CONFIRMED_PIN\"}}}]}";
    }

    private String envelope(ResultActions actions, UUID resourceId) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> parsed = new TreeMap<>(objectMapper.readValue(body,
                new TypeReference<Map<String, Object>>() {
                }));
        parsed.remove("timeStamp");
        parsed.remove("requestId");
        return parsed.toString().replace(resourceId.toString(), "{istenen-id}");
    }

    private void insertAssignment(UUID id, UUID worker) {
        jdbc.update("insert into job_assignments (id, job_id, worker_id, status, payment_status, work_hours, "
                + "is_deleted, created_at) values (?, ?, ?, 'CONFIRMED', 'HELD_IN_ESCROW', 0, false, now())",
                id, jobId, worker);
    }

    private void insertUser(UUID id, String firstName, String lastName) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, first_name, last_name, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', ?, ?, false, true, false, false)",
                id, "boarding-" + id + "@example.com", firstName, lastName);
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, "boarding-" + id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
