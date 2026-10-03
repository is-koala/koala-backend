package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.core.configs.BoardingLateSyncGuard;
import com.exskylab.koala.core.dtos.transport.request.SeatBoardingDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleBoardingDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingEventDto;
import com.exskylab.koala.core.helpers.BoardingTokens;
import com.exskylab.koala.core.privacy.AccountAnonymizer;
import com.exskylab.koala.core.properties.AuthLinkProperties;
import com.exskylab.koala.core.properties.TransportBoardingProperties;
import com.exskylab.koala.core.properties.TransportReminderProperties;
import com.exskylab.koala.core.schedulers.TransportReminderScheduler;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.dataAccess.ShuttleDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.Shuttle;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import com.exskylab.koala.support.OverlappingWrites;
import com.exskylab.koala.support.TestLatches;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private AuthLinkProperties authLinkProperties;

    @Autowired
    private TransportService transportService;

    @Autowired
    private ShuttleDao shuttleDao;

    @Autowired
    private AccountAnonymizer accountAnonymizer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

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
    private final Instant laterDeparture = departure.plus(Duration.ofHours(2));
    private final Instant returnDeparture = departure.plus(Duration.ofHours(9));
    private final List<UUID> extraUsers = new ArrayList<>();
    private boolean noticesWereEnabled;
    private Duration tokenTtlBefore;

    private UUID shuttleA;
    private UUID shuttleB;
    private UUID returnShuttle;

    @BeforeEach
    void setUp() throws Exception {
        noticesWereEnabled = boardingProperties.isNoticesEnabled();
        boardingProperties.setNoticesEnabled(true);
        tokenTtlBefore = boardingProperties.getTokenTtl();
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
        boardingProperties.setNoticesEnabled(noticesWereEnabled);
        boardingProperties.setTokenTtl(tokenTtlBefore);
        dropTrigger("delay_boarding_event_insert");
        dropTrigger("duplicate_boarding_event_insert");
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
        List<UUID> everyone = new ArrayList<>(users);
        everyone.addAll(extraUsers);
        for (UUID id : everyone) {
            jdbc.update("delete from notifications where recipient_id = ?", id);
            jdbc.update("delete from devices where user_id = ?", id);
            jdbc.update("delete from account_anonymization_audit where request_id in "
                    + "(select id from account_deletion_requests where user_id = ?)", id);
            jdbc.update("delete from account_deletion_requests where user_id = ?", id);
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
        for (UUID id : everyone) {
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
                .andExpect(jsonPath("$.data.static").value(false)));
        String token = qr.get("token").asText();
        assertTrue(token.startsWith("s1." + shuttleA + "."), token);
        assertEquals(authLinkProperties.getAppScheme() + "://board?t=" + token, qr.get("uri").asText());
        Instant issuedAt = Instant.parse(qr.get("issuedAt").asText());
        Instant refreshAt = Instant.parse(qr.get("refreshAt").asText());
        Instant expiresAt = Instant.parse(qr.get("expiresAt").asText());
        assertFalse(issuedAt.isBefore(before.truncatedTo(ChronoUnit.SECONDS)));
        assertTrue(refreshAt.isAfter(issuedAt), "the screen must refresh after the token was minted");
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
                .andExpect(jsonPath("$.data.stopId").value(stopOf(shuttleA).toString()))
                .andExpect(jsonPath("$.data.recordedById").value(workerId.toString()))
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andExpect(jsonPath("$.data.flags", hasSize(0))));
        assertEquals(capturedAt, Instant.parse(event.get("occurredAt").asText()),
                "the boarding time must not be clamped to the job window");
        assertEquals(capturedAt, Instant.parse(event.get("capturedAt").asText()));
        assertEquals("", jdbc.queryForObject("select flags from boarding_events where seat_assignment_id = ?",
                String.class, seatId));
    }

    @Test
    void anOutboundBoardingFarFromTheStopAndOffItsScheduleIsRecordedWithBothFlags() throws Exception {
        pickSeat(workerId, assignmentId, shuttleB);
        Instant capturedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        JsonNode event = data(scan(workerId, shuttleB, shuttleToken(shuttleB, false), capturedAt, FAR_LAT, FAR_LNG,
                UUID.randomUUID())
                .andExpect(status().isOk()));

        assertEquals(Set.of("FAR_FROM_STOP", "OFF_SCHEDULE"), flagsOf(event));
        assertEquals(capturedAt, Instant.parse(event.get("occurredAt").asText()));
    }

    @Test
    void aCaptureTimeOutsideTheTokenWindowIsPulledIntoTheWindowAndFlagged() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        JsonNode qr = data(mockMvc.perform(get("/shuttles/{id}/qr", shuttleA).with(user(principal(employerId))))
                .andExpect(status().isOk()));
        Instant windowStart = Instant.parse(qr.get("refreshAt").asText()).minus(boardingProperties.getTokenTtl());
        Instant backDated = Instant.now().minus(Duration.ofMinutes(50)).truncatedTo(ChronoUnit.MILLIS);

        JsonNode event = data(scan(workerId, shuttleA, qr.get("token").asText(), backDated, null, null,
                UUID.randomUUID()).andExpect(status().isOk()));

        assertEquals(windowStart, Instant.parse(event.get("occurredAt").asText()),
                "a token minted now cannot prove a boarding fifty minutes ago");
        assertEquals(backDated, Instant.parse(event.get("capturedAt").asText()));
        assertTrue(flagsOf(event).contains("DEVICE_TIME_IMPLAUSIBLE"), event.toString());
    }

    @Test
    void aLateOfflineUploadIsAcceptedOnlyWhileTheTokenWasValidWhenScanned() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        Instant scannedAt = Instant.now().minus(Duration.ofMinutes(21)).truncatedTo(ChronoUnit.MILLIS);
        String offline = BoardingTokens.mintShuttle(secretOf(shuttleA), shuttleA, scannedAt,
                boardingProperties.getTokenTtl(), false);

        JsonNode event = data(scan(workerId, shuttleA, offline, scannedAt, null, null, UUID.randomUUID())
                .andExpect(status().isOk()));
        assertEquals(Set.of("LATE_SYNC"), flagsOf(event));
        assertEquals(scannedAt, Instant.parse(event.get("occurredAt").asText()));

        String longGone = BoardingTokens.mintShuttle(secretOf(shuttleA), shuttleA,
                Instant.now().minus(Duration.ofMinutes(40)), boardingProperties.getTokenTtl(), false);
        scan(secondWorkerId, shuttleA, longGone, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_TOKEN_EXPIRED"));
        Instant tooLate = Instant.now().minus(boardingProperties.getLateSyncMax()).minus(Duration.ofMinutes(5));
        String yesterday = BoardingTokens.mintShuttle(secretOf(shuttleA), shuttleA, tooLate,
                boardingProperties.getTokenTtl(), false);
        scan(secondWorkerId, shuttleA, yesterday, tooLate, null, null, UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_TOKEN_EXPIRED"));
        assertEquals(1, boardingRows());
    }

    @Test
    void aPreviousWindowTokenProvesTheBoardingUntilItsExpiresAtOnlineAndOffline() throws Exception {
        Duration ttl = tokenTtlWithTheCurrentWindowAged(Duration.ofMinutes(8), Duration.ofMinutes(2));
        boardingProperties.setTokenTtl(ttl);
        pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        Instant currentWindow = BoardingTokens.refreshAt(Instant.now(), ttl).minus(ttl);
        Instant previousWindow = currentWindow.minus(ttl);
        Instant twoWindowsBack = previousWindow.minus(ttl);

        String stillOnTheTablet = BoardingTokens.mintShuttle(secretOf(shuttleA), shuttleA, previousWindow, ttl, false);
        Instant scannedOnline = BoardingTokens.refreshAt(previousWindow, ttl).plus(Duration.ofMinutes(5));
        JsonNode online = data(scan(workerId, shuttleA, stillOnTheTablet, scannedOnline, null, null, UUID.randomUUID())
                .andExpect(status().isOk()));
        assertEquals(scannedOnline, Instant.parse(online.get("occurredAt").asText()),
                "a token that still scans proves the boarding up to its expiresAt, not its refreshAt");
        assertFalse(flagsOf(online).contains("DEVICE_TIME_IMPLAUSIBLE"), online.toString());
        assertFalse(flagsOf(online).contains("LATE_SYNC"), online.toString());

        String scannedThenUploadedLater = BoardingTokens.mintShuttle(secretOf(shuttleA), shuttleA, twoWindowsBack, ttl,
                false);
        Instant scannedOffline = BoardingTokens.refreshAt(twoWindowsBack, ttl).plus(Duration.ofMinutes(5));
        assertTrue(Instant.now().isAfter(BoardingTokens.acceptedUntil(twoWindowsBack, ttl)),
                "the upload must arrive after the token's expiresAt");
        JsonNode offline = data(scan(secondWorkerId, shuttleA, scannedThenUploadedLater, scannedOffline, null, null,
                UUID.randomUUID()).andExpect(status().isOk()));
        assertEquals(scannedOffline, Instant.parse(offline.get("occurredAt").asText()),
                "the same scan uploaded late keeps the time the online scan would have kept");
        assertTrue(flagsOf(offline).contains("LATE_SYNC"), offline.toString());
        assertFalse(flagsOf(offline).contains("DEVICE_TIME_IMPLAUSIBLE"), offline.toString());
        assertEquals(2, boardingRows());
    }

    @Test
    void aSeatMovedOffTheScannedShuttleWhileTheScanWaitsForItsLockIsConcealed() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleB);
        String onB = data(scan(workerId, shuttleB, shuttleToken(shuttleB, false), Instant.now(), null, null,
                UUID.randomUUID()).andExpect(status().isOk())).get("id").asText();
        assertEquals(seatId, pickSeat(employerId, assignmentId, shuttleA));
        String tokenOfA = shuttleToken(shuttleA, false);
        UUID stopOfB = stopOf(shuttleB);

        HikariDataSource pool = (HikariDataSource) dataSource;
        try (Connection mover = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
             Connection observer = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(),
                     pool.getPassword())) {
            mover.setAutoCommit(false);
            try (PreparedStatement move = mover.prepareStatement(
                    "update seat_assignments set shuttle_id = ?, stop_id = ? where id = ?")) {
                move.setObject(1, shuttleB);
                move.setObject(2, stopOfB);
                move.setObject(3, seatId);
                assertEquals(1, move.executeUpdate());
            }
            CompletableFuture<String> scanner = CompletableFuture.supplyAsync(() -> {
                String[] outcome = new String[1];
                try {
                    asUser(workerId, () -> {
                        BoardingEventDto event = transportService.boardOnShuttle(shuttleA,
                                new ShuttleBoardingDto(tokenOfA, Instant.now(), null, null, UUID.randomUUID()));
                        outcome[0] = "EVENT " + event.getId() + " replayed=" + event.isReplayed();
                        return event;
                    });
                } catch (RuntimeException refused) {
                    outcome[0] = refused.getClass().getSimpleName() + ": " + refused.getMessage();
                }
                return outcome[0];
            });
            try {
                awaitAScanQueuedOnTheSeatLock(observer);
            } finally {
                mover.commit();
            }
            String outcome = TestLatches.getProgress(scanner);

            assertTrue(outcome.startsWith("ResourceNotFoundException"),
                    "the seat now rides shuttle B; scanning A must not answer with B's boarding " + onB + ": " + outcome);
        }
        assertEquals(1, boardingRows());
    }

    @Test
    void anOfflineRetryOfASavedBoardingIsAnsweredWithoutLookingAtTheToken() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        UUID retryId = UUID.randomUUID();
        Instant capturedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String eventId = data(scan(workerId, shuttleA, shuttleToken(shuttleA, false), capturedAt, NEAR_LAT, NEAR_LNG,
                retryId).andExpect(status().isOk())).get("id").asText();
        String expired = BoardingTokens.mintShuttle(secretOf(shuttleA), shuttleA,
                Instant.now().minus(Duration.ofMinutes(40)), boardingProperties.getTokenTtl(), false);
        String expiredWorker = BoardingTokens.mintWorker(secretOf(shuttleA), seatId,
                Instant.now().minus(Duration.ofMinutes(40)), boardingProperties.getTokenTtl());

        scan(workerId, shuttleA, expired, capturedAt, NEAR_LAT, NEAR_LNG, retryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId))
                .andExpect(jsonPath("$.data.replayed").value(true));
        scan(workerId, shuttleA, "s1.garbage", Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId))
                .andExpect(jsonPath("$.data.replayed").value(true));
        mark(employerId, seatId, "{\"token\":\"" + expiredWorker + "\",\"capturedAt\":\"" + Instant.now() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId))
                .andExpect(jsonPath("$.data.replayed").value(true));
        assertEquals(1, boardingRows());
    }

    @Test
    void theEmployerScansTheWorkerQrOfEitherDirection() throws Exception {
        UUID outboundSeat = pickSeat(workerId, assignmentId, shuttleA);
        UUID returnSeat = pickSeat(workerId, assignmentId, returnShuttle);

        JsonNode outboundQr = data(mockMvc.perform(get("/job-assignments/{id}/transport/qr", assignmentId)
                        .with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.static").value(false)));
        assertTrue(outboundQr.get("token").asText().startsWith("w1." + outboundSeat + "."));
        assertEquals(authLinkProperties.getAppScheme() + "://board?t=" + outboundQr.get("token").asText(),
                outboundQr.get("uri").asText());

        JsonNode returnQr = data(mockMvc.perform(get("/job-assignments/{id}/transport/qr", assignmentId)
                        .param("direction", "RETURN")
                        .with(user(principal(workerId))))
                .andExpect(status().isOk()));
        assertTrue(returnQr.get("token").asText().startsWith("w1." + returnSeat + "."));

        mark(employerId, returnSeat, "{\"token\":\"" + returnQr.get("token").asText() + "\",\"capturedAt\":\""
                + Instant.now().truncatedTo(ChronoUnit.MILLIS) + "\",\"latitude\":" + FAR_LAT + ",\"longitude\":"
                + FAR_LNG + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("BOARDED"))
                .andExpect(jsonPath("$.data.source").value("EMPLOYER_SCANNED_WORKER_QR"))
                .andExpect(jsonPath("$.data.shuttleId").value(returnShuttle.toString()))
                .andExpect(jsonPath("$.data.recordedById").value(employerId.toString()))
                .andExpect(jsonPath("$.data.flags", hasSize(0)));
        mark(contactId, outboundSeat, "{\"token\":\"" + outboundQr.get("token").asText() + "\",\"capturedAt\":\""
                + Instant.now().truncatedTo(ChronoUnit.MILLIS) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatAssignmentId").value(outboundSeat.toString()));

        UUID secondSeat = pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        mark(employerId, secondSeat, "{\"token\":\"" + returnQr.get("token").asText() + "\",\"capturedAt\":\""
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
    void aStaleTokenIs400AndOnlyJobPartiesLearnWhyTheyCannotBoard() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(thirdWorkerId, thirdAssignmentId, shuttleA);
        jdbc.update("update job_assignments set status = 'CANCELLED' where id = ?", thirdAssignmentId);
        String stale = BoardingTokens.mintShuttle(secretOf(shuttleA), shuttleA,
                Instant.now().minus(Duration.ofMinutes(25)), boardingProperties.getTokenTtl(), false);
        scan(workerId, shuttleA, stale, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_TOKEN_EXPIRED"));

        String tokenOfA = shuttleToken(shuttleA, false);
        String tokenOfB = shuttleToken(shuttleB, false);
        scan(workerId, shuttleB, tokenOfB, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_WRONG_SHUTTLE"));
        scan(secondWorkerId, shuttleA, tokenOfA, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SEAT_NOT_SELECTED"));
        scan(thirdWorkerId, shuttleA, tokenOfA, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_ASSIGNMENT_NOT_ACTIVE"));

        UUID phantom = UUID.randomUUID();
        String missing = envelope(scan(workerId, phantom, tokenOfB, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isNotFound()), phantom);
        assertEquals(missing, envelope(scan(workerId, shuttleA, tokenOfB, Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isNotFound()), shuttleA), "another shuttle's token is just an invalid token");
        assertEquals(missing, envelope(scan(outsiderId, shuttleA, tokenOfA, Instant.now(), null, null,
                UUID.randomUUID()).andExpect(status().isNotFound()), shuttleA), "a genuine token does not help a stranger");
        assertEquals(missing, envelope(scan(secondWorkerId, shuttleB, "s1.garbage", Instant.now(), null, null,
                UUID.randomUUID()).andExpect(status().isNotFound()), shuttleB), "a party with garbage stays concealed");
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(false))).get("id").asText();

        scan(workerId, shuttleA, token, capturedAt.plusSeconds(20), NEAR_LAT, NEAR_LNG, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId))
                .andExpect(jsonPath("$.data.replayed").value(true));
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
    void aWorkerMovedToAnotherShuttleTheSameDayBoardsThereOnItsOwnRecord() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        String onA = data(scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), null, null,
                UUID.randomUUID()).andExpect(status().isOk())).get("id").asText();
        jdbc.update("update shuttle_stops set scheduled_at = ? where shuttle_id = ?",
                java.sql.Timestamp.from(departure.plus(Duration.ofMinutes(20))), shuttleB);
        assertEquals(seatId, pickSeat(employerId, assignmentId, shuttleB), "the move keeps the same seat row");

        mockMvc.perform(get("/shuttles/{id}/boardings", shuttleB).with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].boarded").value(false))
                .andExpect(jsonPath("$.data[0].event").doesNotExist());
        mockMvc.perform(get("/job-assignments/{id}/transport", assignmentId).with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seats[0].boarding").doesNotExist());

        String onB = data(scan(workerId, shuttleB, shuttleToken(shuttleB, false), Instant.now(), null, null,
                UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shuttleId").value(shuttleB.toString()))
                .andExpect(jsonPath("$.data.replayed").value(false))).get("id").asText();

        assertNotEquals(onA, onB);
        mockMvc.perform(get("/job-assignments/{id}/transport", assignmentId).with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seats[0].boarding.kind").value("BOARDED"))
                .andExpect(jsonPath("$.data.seats[0].boarding.occurredAt").exists());
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
                .andExpect(jsonPath("$.data.id").value(noShow))
                .andExpect(jsonPath("$.data.replayed").value(true));
        mockMvc.perform(get("/job-assignments/{id}/transport", assignmentId).with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seats[0].boarding.kind").value("NO_SHOW"));

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
    void aCompletedAssignmentStillBoardsAndAReturnSeatCarriesNoPickupFlags() throws Exception {
        UUID returnSeat = pickSeat(workerId, assignmentId, returnShuttle);
        jdbc.update("update job_assignments set status = 'COMPLETED' where id = ?", assignmentId);

        String token = data(mockMvc.perform(get("/job-assignments/{id}/transport/qr", assignmentId)
                        .param("direction", "RETURN")
                        .with(user(principal(workerId))))
                .andExpect(status().isOk())).get("token").asText();
        mark(employerId, returnSeat, "{\"token\":\"" + token + "\",\"capturedAt\":\""
                + Instant.now().truncatedTo(ChronoUnit.MILLIS) + "\",\"latitude\":" + FAR_LAT + ",\"longitude\":"
                + FAR_LNG + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("BOARDED"))
                .andExpect(jsonPath("$.data.flags", hasSize(0)));
        scan(workerId, returnShuttle, shuttleToken(returnShuttle, false), Instant.now(), FAR_LAT, FAR_LNG,
                UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true));
        mockMvc.perform(get("/shuttles/{id}/boardings", returnShuttle).with(user(principal(contactId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].boarded").value(true));
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
        assertEquals("Kadıköy İskele", boarded.get("stopLabel").asText());
        assertEquals(departure, Instant.parse(boarded.get("scheduledAt").asText()));
        assertEquals(Set.of("FAR_FROM_STOP", "STATIC_QR"), flagsOf(boarded.get("event")));
        assertFalse(boarded.get("event").get("replayed").asBoolean());
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
        String secretBefore = secretOf(shuttleA);

        mockMvc.perform(post("/shuttles/{id}/qr/rotate", shuttleA).with(user(principal(outsiderId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SHUTTLE_NOT_FOUND"));
        assertEquals(secretBefore, secretOf(shuttleA));

        String fresh = data(mockMvc.perform(post("/shuttles/{id}/qr/rotate", shuttleA)
                        .with(user(principal(contactId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.static").value(false))).get("token").asText();
        assertNotEquals(secretBefore, secretOf(shuttleA));

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
    void aPlanSaveRacingARotationCannotWriteTheOldSecretBack() throws Exception {
        String secretBefore = secretOf(shuttleA);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Shuttle stale = shuttleDao.findById(shuttleA).orElseThrow();
            stale.setName("A servisi (yeni ad)");
            CompletableFuture.runAsync(() -> asUser(employerId, () -> transportService.rotateShuttleQr(shuttleA)))
                    .orTimeout(30, TimeUnit.SECONDS)
                    .join();
        });

        assertEquals("A servisi (yeni ad)", jdbc.queryForObject("select name from shuttles where id = ?",
                String.class, shuttleA));
        assertNotEquals(secretBefore, secretOf(shuttleA), "the stale entity flushed the revoked secret back");
    }

    @Test
    void aConstraintViolationBehindTheLockIsAConflictNotAServerError() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        jdbc.execute("""
                create or replace function duplicate_boarding_event_insert() returns trigger as $$
                declare
                    twin boarding_events%rowtype;
                begin
                    if pg_trigger_depth() = 1 then
                        twin := new;
                        twin.id := gen_random_uuid();
                        insert into boarding_events values (twin.*);
                    end if;
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbc.execute("create trigger duplicate_boarding_event_insert before insert on boarding_events "
                + "for each row execute function duplicate_boarding_event_insert()");

        mark(employerId, seatId, "{\"kind\":\"BOARDED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_BOARDING_CONCURRENT"));
        mark(employerId, seatId, "{\"kind\":\"NO_SHOW\",\"clientEventId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_BOARDING_IDEMPOTENCY_CONFLICT"));
        dropTrigger("duplicate_boarding_event_insert");
        assertEquals(0, boardingRows());
    }

    @Test
    void theMarkingRulesAreValidatedAtTheBoundary() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        mark(employerId, seatId, "{\"kind\":\"ALIGHTED\"}")
                .andExpect(status().isBadRequest());
        mark(employerId, seatId, "{\"kind\":\"NO_SHOW\",\"token\":\"w1.x.y.z\",\"capturedAt\":\"" + Instant.now()
                + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("kindConsistentWithToken"));
        mark(employerId, seatId, "{\"latitude\":41.05}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("positionComplete"));
        mark(employerId, seatId, "{\"token\":\"" + "w".repeat(300) + "\",\"capturedAt\":\"" + Instant.now() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("token"));
        mockMvc.perform(post("/shuttles/{id}/boardings", shuttleA).with(user(principal(workerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"s1.x.y.z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("capturedAt"));
        mark(outsiderId, seatId, "{\"kind\":\"BOARDED\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SEAT_ASSIGNMENT_NOT_FOUND"));
        mark(employerId, UUID.randomUUID(), "{\"kind\":\"BOARDED\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SEAT_ASSIGNMENT_NOT_FOUND"));
        jdbc.update("update job_assignments set status = 'CANCELLED' where id = ?", assignmentId);
        mark(employerId, seatId, "{\"kind\":\"BOARDED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_ASSIGNMENT_NOT_ACTIVE"));
        assertEquals(0, boardingRows());
    }

    @Test
    void theStaffTimelineKeepsTheBoardingEvidenceAfterThePlanDropsTheSeat() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        Instant capturedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String eventId = data(scan(workerId, shuttleA, shuttleToken(shuttleA, false), capturedAt, NEAR_LAT, NEAR_LNG,
                UUID.randomUUID()).andExpect(status().isOk())).get("id").asText();
        String token = staffTokenFor("boarding-staff-" + UUID.randomUUID() + "@iskoala.com", "Analist",
                "FINANCE_ANALYST");

        mockMvc.perform(get("/admin/job-assignments/{id}", assignmentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline", hasSize(2)))
                .andExpect(jsonPath("$.data.timeline[0].type").value("SEAT"))
                .andExpect(jsonPath("$.data.timeline[0].seatAssignmentId").value(seatId.toString()))
                .andExpect(jsonPath("$.data.timeline[1].type").value("BOARDING"))
                .andExpect(jsonPath("$.data.timeline[1].id").value(eventId));

        putPlan(employerId, "{\"outboundMethod\":\"SELF\",\"returnMethod\":\"NONE\",\"shuttles\":[]}")
                .andExpect(status().isOk());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select is_deleted from seat_assignments where id = ?",
                Boolean.class, seatId));

        JsonNode boarding = data(mockMvc.perform(get("/admin/job-assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline", hasSize(1)))).get("timeline").get(0);
        assertEquals("BOARDING", boarding.get("type").asText());
        assertEquals(eventId, boarding.get("id").asText());
        assertEquals("BOARDED", boarding.get("kind").asText());
        assertEquals("WORKER_SCANNED_SHUTTLE_QR", boarding.get("source").asText());
        assertEquals("WORKER", boarding.get("recorderRole").asText());
        assertEquals(shuttleA.toString(), boarding.get("shuttleId").asText());
        assertEquals(seatId.toString(), boarding.get("seatAssignmentId").asText());
        assertEquals("Kadıköy İskele", boarding.get("stopLabel").asText());
        assertEquals(departure, Instant.parse(boarding.get("stopScheduledAt").asText()));
        assertEquals(capturedAt, Instant.parse(boarding.get("capturedAt").asText()));
        assertFalse(boarding.get("receivedAt").isNull(), "the server receipt time is evidence too");
        assertEquals(new BigDecimal(NEAR_LAT), boarding.get("latitude").decimalValue().setScale(6));
        assertEquals(new BigDecimal(NEAR_LNG), boarding.get("longitude").decimalValue().setScale(6));
        int meters = boarding.get("distanceToStopMeters").asInt();
        assertTrue(meters > 5 && meters < 30, "about 14 m from the pin, was " + meters);
    }

    @Test
    void deletingAnAccountScrubsTheBoardingLocationsItLeftBehind() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        UUID otherSeat = pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), NEAR_LAT, NEAR_LNG, UUID.randomUUID())
                .andExpect(status().isOk());
        mark(contactId, otherSeat, "{\"kind\":\"NO_SHOW\",\"latitude\":" + FAR_LAT + ",\"longitude\":" + FAR_LNG + "}")
                .andExpect(status().isOk());

        anonymize(workerId);
        assertNull(jdbc.queryForObject("select latitude from boarding_events where seat_assignment_id = ?",
                Object.class, seatId));
        assertNull(jdbc.queryForObject("select longitude from boarding_events where seat_assignment_id = ?",
                Object.class, seatId));
        assertNull(jdbc.queryForObject("select distance_to_stop_meters from boarding_events where seat_assignment_id = ?",
                Object.class, seatId));
        assertNotNull(jdbc.queryForObject("select latitude from boarding_events where seat_assignment_id = ?",
                Object.class, otherSeat), "another worker's row is not the deleted account's data");

        anonymize(contactId);
        assertNull(jdbc.queryForObject("select latitude from boarding_events where seat_assignment_id = ?",
                Object.class, otherSeat), "the contact's phone position is the contact's personal data");
        assertEquals(2, boardingRows(), "the boarding evidence itself stays");
    }

    @Test
    void theLateSyncGuardIsWiredIntoTheRunningApplication() {
        assertEquals(1, applicationContext.getBeansOfType(BoardingLateSyncGuard.class).size());
    }

    @Test
    void deletingTheJobCreatorsAccountScrubsTheBoardingLocationsOfTheJob() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        UUID otherSeat = pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), NEAR_LAT, NEAR_LNG, UUID.randomUUID())
                .andExpect(status().isOk());
        mark(contactId, otherSeat, "{\"kind\":\"NO_SHOW\",\"latitude\":" + FAR_LAT + ",\"longitude\":" + FAR_LNG + "}")
                .andExpect(status().isOk());

        anonymize(employerId);
        for (UUID seat : List.of(seatId, otherSeat)) {
            assertNull(jdbc.queryForObject("select latitude from boarding_events where seat_assignment_id = ?",
                    Object.class, seat), "the creator's job carries the creator's boarding records");
            assertNull(jdbc.queryForObject("select longitude from boarding_events where seat_assignment_id = ?",
                    Object.class, seat));
            assertNull(jdbc.queryForObject(
                    "select distance_to_stop_meters from boarding_events where seat_assignment_id = ?", Object.class,
                    seat));
        }
        assertEquals(2, boardingRows(), "the boarding evidence itself stays");
    }

    @Test
    void theBoardingReminderGoesFifteenMinutesBeforeAnOutboundDepartureToSeatsNotYetSettled() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        UUID waitingSeat = pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, returnShuttle);
        insertPushDevice(secondWorkerId);
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
        Map<String, String> push = pushPayloadOf(secondWorkerId);
        assertEquals("SHUTTLE_REMINDER_BOARDING", push.get("event"));
        assertEquals("OUTBOUND", push.get("direction"));
        assertEquals(waitingSeat.toString(), push.get("seatAssignmentId"));

        reminderScheduler.sendDueReminders(returnDeparture.minus(Duration.ofMinutes(14)));
        reminderScheduler.sendDueReminders(returnDeparture.plus(Duration.ofMinutes(13)));
        assertEquals(1, transportNotices(secondWorkerId), "a return seat is not a pickup point until T3");
        assertEquals(0, transportNotices(employerId));
    }

    @Test
    void theBoardingNoticesStayOffUntilTheyAreEnabledWhileTheT1RemindersKeepGoing() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isOk());
        jdbc.update("update seat_assignments set evening_reminder_sent_at = now() where shuttle_id = ?", shuttleA);
        jdbc.update("delete from notifications where recipient_id in (?, ?)", workerId, secondWorkerId);
        boardingProperties.setNoticesEnabled(false);

        reminderScheduler.sendDueReminders(departure.minus(Duration.ofMinutes(14)));
        reminderScheduler.sendDueReminders(departure.plus(Duration.ofMinutes(13)));

        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_id = ? "
                + "and channel = 'IN_APP' and body like '%QR%'", Integer.class, secondWorkerId));
        assertEquals(0, transportNotices(employerId));
        assertEquals(0, transportNotices(contactId));
        assertEquals(1, transportNotices(secondWorkerId), "the T1 departure reminder is not gated by the T2 flag");
        assertNull(jdbc.queryForObject("select boarding_reminder_sent_at from seat_assignments where "
                + "job_assignment_id = ? and is_deleted = false", Object.class, secondAssignmentId));
    }

    @Test
    void theNotBoardedNoticeIsOneMessagePerDepartureToEveryCurrentEditor() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        UUID noShowSeat = pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        pickSeat(thirdWorkerId, thirdAssignmentId, shuttleA);
        UUID named = extraWorker("Ali", "Gelmeyen");
        UUID unnamed = extraWorker(null, null);
        for (UUID late : List.of(named, unnamed)) {
            UUID lateAssignment = UUID.randomUUID();
            insertAssignment(lateAssignment, late);
            pickSeat(late, lateAssignment, shuttleA);
        }
        scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isOk());
        mark(employerId, noShowSeat, "{\"kind\":\"NO_SHOW\"}").andExpect(status().isOk());
        jdbc.update("update job_assignments set status = 'CANCELLED' where id = ?", thirdAssignmentId);
        silenceTheEarlierReminders();
        jdbc.update("update seat_assignments set boarding_reminder_sent_at = now() where shuttle_id = ?", shuttleA);
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
        assertTrue(body.startsWith("A servisi ("), body);
        assertTrue(body.contains(", Kadıköy İskele)"), body);
        assertTrue(body.contains(" 2 kişi"), body);
        assertTrue(body.contains("Ali Gelmeyen") && body.contains("adı olmayan işçi"),
                "every missing seat is named, even without a name: " + body);
        assertFalse(body.contains("Ayşe"), "a boarded worker is not missing: " + body);
        assertFalse(body.contains("Mehmet"), "a no-show was already settled by the employer: " + body);
        assertFalse(body.contains("Zeynep"), "a cancelled assignment is not expected on the shuttle: " + body);
        assertEquals("JOB", jdbc.queryForObject("select target_type from notifications where recipient_id = ? "
                + "and channel = 'IN_APP'", String.class, employerId));
        assertEquals(jobId, jdbc.queryForObject("select target_id from notifications where recipient_id = ? "
                + "and channel = 'IN_APP'", UUID.class, employerId));
    }

    @Test
    void theNotBoardedNoticeIgnoresDeparturesOlderThanTheLookback() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        scan(workerId, shuttleA, shuttleToken(shuttleA, false), Instant.now(), null, null, UUID.randomUUID())
                .andExpect(status().isOk());
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
    void noNotBoardedNoticeForADepartureNobodyScanned() throws Exception {
        pickSeat(workerId, assignmentId, shuttleA);
        pickSeat(secondWorkerId, secondAssignmentId, shuttleA);
        silenceTheEarlierReminders();
        jdbc.update("update seat_assignments set boarding_reminder_sent_at = now() where shuttle_id = ?", shuttleA);

        reminderScheduler.sendDueReminders(departure.plus(Duration.ofMinutes(13)));

        assertEquals(0, transportNotices(employerId), "the employer is not scanning today; two missing is not news");
        assertEquals(0, transportNotices(contactId));
        assertNotNull(jdbc.queryForObject("select not_boarded_notice_sent_at from seat_assignments "
                + "where job_assignment_id = ? and is_deleted = false", Object.class, secondAssignmentId));
    }

    @Test
    void aWorkerScanAndAnEmployerMarkRacingOnOneSeatLeaveOneBoarding() throws Exception {
        UUID seatId = pickSeat(workerId, assignmentId, shuttleA);
        String token = shuttleToken(shuttleA, false);
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
        OverlappingWrites overlap = new OverlappingWrites();
        List<String> outcomes = overlap.outcomesOf(
                () -> inTransaction(workerId, overlap, () -> transportService.boardOnShuttle(shuttleA,
                        new ShuttleBoardingDto(token, Instant.now(), null, null, UUID.randomUUID()))),
                () -> inTransaction(employerId, overlap, () -> transportService.markSeatBoarding(seatId,
                        new SeatBoardingDto(null, null, null, null, UUID.randomUUID(), null))));
        dropTrigger("delay_boarding_event_insert");

        assertEquals(2, outcomes.stream().filter(outcome -> outcome.startsWith("EVENT ")).count(),
                "both writers must get the boarding: " + outcomes);
        assertEquals(outcomes.get(0), outcomes.get(1), "both writers must see one and the same event");
        assertEquals(1, boardingRows());
    }

    private interface Write {
        BoardingEventDto run();
    }

    private interface Call {
        Object run();
    }

    private String inTransaction(UUID actorId, OverlappingWrites overlap, Write write) {
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

    private void asUser(UUID actorId, Call call) {
        UserPrincipal principal = principal(actorId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            call.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Duration tokenTtlWithTheCurrentWindowAged(Duration atLeast, Duration headroom) {
        long now = Instant.now().getEpochSecond();
        for (long minutes = 10; minutes <= 120; minutes++) {
            long window = minutes * 60;
            long elapsed = Math.floorMod(now, window);
            if (elapsed >= atLeast.toSeconds() && window - elapsed >= headroom.toSeconds()) {
                return Duration.ofMinutes(minutes);
            }
        }
        throw new IllegalStateException("no token window of 10-120 minutes is " + atLeast + " old right now");
    }

    private static void awaitAScanQueuedOnTheSeatLock(Connection observer) throws Exception {
        long deadline = System.nanoTime() + TestLatches.progressTimeout().toNanos();
        while (true) {
            try (Statement statement = observer.createStatement();
                 ResultSet waiting = statement.executeQuery("""
                         select count(*) from pg_stat_activity
                         where datname = current_database()
                           and pid <> pg_backend_pid()
                           and wait_event_type = 'Lock'
                           and query ilike '%seat_assignments%'
                         """)) {
                waiting.next();
                if (waiting.getInt(1) > 0) {
                    return;
                }
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("the scan never queued behind the seat lock");
            }
            Thread.sleep(20);
        }
    }

    private void anonymize(UUID userId) {
        UUID requestId = UUID.randomUUID();
        jdbc.update("insert into account_deletion_requests (id, user_id, subject_id, status, requested_at, created_at, "
                + "is_deleted) values (?, ?, ?, 'REQUESTED', now(), now(), false)", requestId, userId, userId);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accountAnonymizer.anonymize(userId, requestId));
    }

    private void dropTrigger(String name) {
        jdbc.execute("drop trigger if exists " + name + " on boarding_events");
        jdbc.execute("drop function if exists " + name + "()");
    }

    private void silenceTheEarlierReminders() {
        jdbc.update("update seat_assignments set evening_reminder_sent_at = now(), departure_reminder_sent_at = now() "
                + "where shuttle_id in (?, ?, ?)", shuttleA, shuttleB, returnShuttle);
    }

    private int transportNotices(UUID recipient) {
        return jdbc.queryForObject("select count(*) from notifications where recipient_id = ? and channel = 'IN_APP' "
                + "and category = 'TRANSPORT'", Integer.class, recipient);
    }

    private Map<String, String> pushPayloadOf(UUID recipient) throws Exception {
        String payload = jdbc.queryForObject("select push_data_payload from notifications where recipient_id = ? "
                + "and channel = 'PUSH' and category = 'TRANSPORT'", String.class, recipient);
        return objectMapper.readValue(payload, new TypeReference<Map<String, String>>() {
        });
    }

    private void insertPushDevice(UUID userId) {
        jdbc.update("insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token) "
                        + "values (?, now(), false, ?, 'ANDROID', 'boarding-device', ?, ?)",
                UUID.randomUUID(), userId, UUID.randomUUID().toString(), "boarding-token-" + userId);
    }

    private int boardingRows() {
        return jdbc.queryForObject("select count(*) from boarding_events where job_assignment_id in "
                + "(select id from job_assignments where job_id = ?)", Integer.class, jobId);
    }

    private String secretOf(UUID shuttleId) {
        return jdbc.queryForObject("select qr_secret from shuttles where id = ?", String.class, shuttleId);
    }

    private Set<String> flagsOf(JsonNode event) {
        Set<String> flags = new TreeSet<>();
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
        mockMvc.perform(put("/job-assignments/{id}/transport/seat", assignment)
                        .with(user(principal(actorId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shuttleId\":\"" + shuttleId + "\",\"stopId\":\"" + stopOf(shuttleId) + "\"}"))
                .andExpect(status().isOk());
        String direction = jdbc.queryForObject("select direction from shuttles where id = ?", String.class, shuttleId);
        return seatOf(assignment, direction);
    }

    private UUID stopOf(UUID shuttleId) {
        return jdbc.queryForObject("select id from shuttle_stops where shuttle_id = ? and is_deleted = false",
                UUID.class, shuttleId);
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
                + shuttleJson("OUTBOUND", "B servisi", "34 XYZ 99", laterDeparture) + ","
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

    private UUID extraWorker(String firstName, String lastName) {
        UUID id = UUID.randomUUID();
        insertUser(id, firstName, lastName);
        extraUsers.add(id);
        return id;
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
