package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.core.dtos.transport.request.SeatSelectionDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.schedulers.TransportReminderScheduler;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransportPlanIT extends AbstractIntegrationTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final String PLATE = "34 ABC 12";
    private static final String OTHER_PLATE = "34 XYZ 99";
    private static final String DRIVER_PHONE = "+905551112233";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransportReminderScheduler reminderScheduler;

    @Autowired
    private TransportService transportService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID secondWorkerId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID jobAddressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID otherJobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID secondAssignmentId = UUID.randomUUID();
    private final String title = "Ulaşım testi " + UUID.randomUUID();
    private final LocalDate jobDay = LocalDate.now(ISTANBUL).plusDays(10);

    @BeforeEach
    void setUp() {
        insertUser(employerId);
        insertUser(workerId);
        insertUser(secondWorkerId);
        insertUser(outsiderId);
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
                categoryId, "Test", "TEST_" + categoryId);
        insertJob(jobId, title);
        insertJob(otherJobId, "Diğer ilan " + otherJobId);
        insertAssignment(assignmentId, workerId);
        insertAssignment(secondAssignmentId, secondWorkerId);
    }

    @AfterEach
    void tearDown() {
        for (UUID job : List.of(jobId, otherJobId)) {
            List<UUID> stopAddresses = jdbc.queryForList("select st.address_id from shuttle_stops st "
                    + "join shuttles s on s.id = st.shuttle_id join transport_plans p on p.id = s.plan_id "
                    + "where p.job_id = ?", UUID.class, job);
            jdbc.update("delete from seat_assignments where shuttle_id in (select s.id from shuttles s "
                    + "join transport_plans p on p.id = s.plan_id where p.job_id = ?)", job);
            jdbc.update("delete from shuttle_stops where shuttle_id in (select s.id from shuttles s "
                    + "join transport_plans p on p.id = s.plan_id where p.job_id = ?)", job);
            jdbc.update("delete from shuttles where plan_id in (select id from transport_plans where job_id = ?)", job);
            jdbc.update("delete from transport_plans where job_id = ?", job);
            stopAddresses.forEach(id -> jdbc.update("delete from addresses where id = ?", id));
        }
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?, ?)",
                employerId, workerId, secondWorkerId, outsiderId);
        jdbc.update("delete from devices where user_id in (?, ?, ?, ?)", employerId, workerId, secondWorkerId, outsiderId);
        jdbc.update("delete from job_assignments where job_id in (?, ?)", jobId, otherJobId);
        jdbc.update("delete from jobs where id in (?, ?)", jobId, otherJobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", jobAddressId);
        jdbc.update("delete from addresses where district_id = ?", districtId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?)",
                employerId, workerId, secondWorkerId, outsiderId);
        jdbc.update("delete from users where id in (?, ?, ?, ?)", employerId, workerId, secondWorkerId, outsiderId);
    }

    @Test
    void theEmployerWritesAndReadsThePlanWithEverySensitiveField() throws Exception {
        putPlan(employerId, jobId, planJson(null, null, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.outboundMethod").value("SHUTTLE"))
                .andExpect(jsonPath("$.data.returnMethod").value("CASH_ALLOWANCE"))
                .andExpect(jsonPath("$.data.allowanceAmount").value(150.0))
                .andExpect(jsonPath("$.data.shuttles", hasSize(1)))
                .andExpect(jsonPath("$.data.shuttles[0].plate").value(PLATE))
                .andExpect(jsonPath("$.data.shuttles[0].driverPhone").value(DRIVER_PHONE))
                .andExpect(jsonPath("$.data.shuttles[0].seatsTaken").value(0))
                .andExpect(jsonPath("$.data.shuttles[0].stops", hasSize(1)))
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].address.components.thoroughfare")
                        .value("Barbaros Bulvarı"));

        mockMvc.perform(get("/jobs/{jobId}/transport-plan", jobId).with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shuttles[0].plate").value(PLATE))
                .andExpect(jsonPath("$.data.shuttles[0].driverName").value("Ahmet Şoför"))
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].scheduledAt").value(departureAt().toString()));
    }

    @Test
    void aStrangerCannotTellTheJobFromOneThatDoesNotExist() throws Exception {
        UUID phantom = UUID.randomUUID();
        assertEquals(
                envelope(putPlan(outsiderId, phantom, planJson(null, null, 2)).andExpect(status().isNotFound()), phantom),
                envelope(putPlan(outsiderId, jobId, planJson(null, null, 2)).andExpect(status().isNotFound()), jobId));
        assertEquals(
                envelope(mockMvc.perform(get("/jobs/{jobId}/transport-plan", phantom)
                        .with(user(principal(outsiderId)))).andExpect(status().isNotFound()), phantom),
                envelope(mockMvc.perform(get("/jobs/{jobId}/transport-plan", jobId)
                        .with(user(principal(outsiderId)))).andExpect(status().isNotFound()), jobId));
        assertEquals(0, jdbc.queryForObject("select count(*) from transport_plans where job_id = ?", Integer.class, jobId));
    }

    @Test
    void thePublicJobDetailCarriesOnlyDistrictLevelStopsAndTheListCarriesTheBadge() throws Exception {
        putPlan(employerId, jobId, planJson(null, null, 2)).andExpect(status().isOk());

        String body = mockMvc.perform(get("/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transport.outboundMethod").value("SHUTTLE"))
                .andExpect(jsonPath("$.data.transport.shuttleCount").value(1))
                .andExpect(jsonPath("$.data.transport.shuttles[0].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.data.transport.shuttles[0].name").value("Anadolu servisi"))
                .andExpect(jsonPath("$.data.transport.shuttles[0].stops[0].districtName").value("Kadıköy"))
                .andExpect(jsonPath("$.data.transport.shuttles[0].stops[0].neighborhood").value("Yıldız Mah."))
                .andExpect(jsonPath("$.data.transport.shuttles[0].stops[0].scheduledAt").value(departureAt().toString()))
                .andExpect(jsonPath("$.data.transport.shuttles[0].plate").doesNotExist())
                .andExpect(jsonPath("$.data.transport.shuttles[0].stops[0].address").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains(PLATE), body);
        assertFalse(body.contains(DRIVER_PHONE), body);
        assertFalse(body.contains("Ahmet"), body);
        assertFalse(body.contains("Barbaros"), body);

        mockMvc.perform(get("/jobs").param("query", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.content[0].hasShuttle").value(true));

        mockMvc.perform(get("/jobs/{jobId}", otherJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transport").doesNotExist());
    }

    @Test
    void theWorkerPicksASeatAndSeesTheDriverOnlyOnThatShuttle() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(1);
        UUID stopId = stopIdOf(shuttleId);

        String before = mockMvc.perform(get("/job-assignments/{id}/transport", assignmentId)
                        .with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seats", hasSize(0)))
                .andExpect(jsonPath("$.data.shuttles[0].plate").doesNotExist())
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].address.components.thoroughfare")
                        .value("Barbaros Bulvarı"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(before.contains(DRIVER_PHONE), before);

        selectSeat(workerId, assignmentId, shuttleId, stopId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seats", hasSize(1)))
                .andExpect(jsonPath("$.data.seats[0].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.data.seats[0].shuttleId").value(shuttleId.toString()))
                .andExpect(jsonPath("$.data.seats[0].stopId").value(stopId.toString()))
                .andExpect(jsonPath("$.data.shuttles[0].plate").value(PLATE))
                .andExpect(jsonPath("$.data.shuttles[0].driverPhone").value(DRIVER_PHONE))
                .andExpect(jsonPath("$.data.shuttles[0].seatsTaken").value(1))
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].seatsTaken").value(1));

        selectSeat(workerId, assignmentId, shuttleId, stopId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seats", hasSize(1)));
        assertEquals(1, jdbc.queryForObject("select count(*) from seat_assignments where job_assignment_id = ?",
                Integer.class, assignmentId));

        mockMvc.perform(get("/job-assignments/{id}", assignmentId).with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transport.seats[0].shuttleId").value(shuttleId.toString()))
                .andExpect(jsonPath("$.data.transport.shuttles[0].plate").value(PLATE));

        mockMvc.perform(get("/users/me/assignments").with(user(principal(secondWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].transport.seats", hasSize(0)))
                .andExpect(jsonPath("$.data.content[0].transport.shuttles[0].plate").doesNotExist());

        mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].transport.shuttles[0].plate", everyItem(is(PLATE))))
                .andExpect(jsonPath("$.data.content[*].transport.shuttles[0].plate", hasSize(2)));
    }

    @Test
    void aFullShuttleRefusesTheNextSeatAndAForeignShuttleIsConcealed() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(1);
        UUID stopId = stopIdOf(shuttleId);
        selectSeat(workerId, assignmentId, shuttleId, stopId).andExpect(status().isOk());

        selectSeat(secondWorkerId, secondAssignmentId, shuttleId, stopId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SHUTTLE_FULL"));

        putPlan(employerId, otherJobId, planJson(null, null, 3)).andExpect(status().isOk());
        UUID foreignShuttleId = jdbc.queryForObject("select s.id from shuttles s join transport_plans p on p.id = s.plan_id "
                + "where p.job_id = ?", UUID.class, otherJobId);
        UUID foreignStopId = stopIdOf(foreignShuttleId);
        UUID phantom = UUID.randomUUID();
        assertEquals(
                envelope(selectSeat(secondWorkerId, secondAssignmentId, phantom, foreignStopId)
                        .andExpect(status().isNotFound()), phantom),
                envelope(selectSeat(secondWorkerId, secondAssignmentId, foreignShuttleId, foreignStopId)
                        .andExpect(status().isNotFound()), foreignShuttleId));

        assertEquals(
                envelope(mockMvc.perform(get("/job-assignments/{id}/transport", phantom)
                        .with(user(principal(outsiderId)))).andExpect(status().isNotFound()), phantom),
                envelope(mockMvc.perform(get("/job-assignments/{id}/transport", assignmentId)
                        .with(user(principal(outsiderId)))).andExpect(status().isNotFound()), assignmentId));
    }

    @Test
    void theEmployerMayPickTheSeatOnBehalfOfTheWorkerAndCapacityCannotDropBelowSeats() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(2);
        UUID stopId = stopIdOf(shuttleId);
        selectSeat(employerId, assignmentId, shuttleId, stopId).andExpect(status().isOk());
        selectSeat(employerId, secondAssignmentId, shuttleId, stopId).andExpect(status().isOk());

        putPlan(employerId, jobId, planJson(shuttleId, stopId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SHUTTLE_CAPACITY_BELOW_SEATS"));

        putPlan(employerId, jobId, planJson(shuttleId, stopId, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shuttles[0].id").value(shuttleId.toString()))
                .andExpect(jsonPath("$.data.shuttles[0].capacity").value(3))
                .andExpect(jsonPath("$.data.shuttles[0].seatsTaken").value(2))
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].id").value(stopId.toString()));
    }

    @Test
    void rewritingThePlanWithoutTheShuttleDropsItsSeatsAndNotifiesTheAssignedWorkers() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(2);
        UUID stopId = stopIdOf(shuttleId);
        selectSeat(workerId, assignmentId, shuttleId, stopId).andExpect(status().isOk());
        jdbc.update("delete from notifications where recipient_id in (?, ?)", workerId, secondWorkerId);

        putPlan(employerId, jobId, "{\"outboundMethod\":\"SELF\",\"returnMethod\":\"NONE\",\"shuttles\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shuttles", hasSize(0)));

        assertEquals(0, jdbc.queryForObject("select count(*) from seat_assignments where job_assignment_id = ? "
                + "and is_deleted = false", Integer.class, assignmentId));
        assertEquals(1, jdbc.queryForObject("select count(*) from shuttles where id = ? and is_deleted = true",
                Integer.class, shuttleId));
        for (UUID recipient : List.of(workerId, secondWorkerId)) {
            assertEquals(1, jdbc.queryForObject("select count(*) from notifications where recipient_id = ? "
                    + "and channel = 'IN_APP' and category = 'TRANSPORT' and target_type = 'JOB_ASSIGNMENT'",
                    Integer.class, recipient));
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_id = ?",
                Integer.class, outsiderId));

        mockMvc.perform(get("/jobs").param("query", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].hasShuttle").value(false));
    }

    @Test
    void theReminderSchedulerSendsTheEveningAndDepartureRemindersOnce() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(2);
        UUID stopId = stopIdOf(shuttleId);
        selectSeat(workerId, assignmentId, shuttleId, stopId).andExpect(status().isOk());
        jdbc.update("delete from notifications where recipient_id in (?, ?)", workerId, secondWorkerId);

        Instant eveningBefore = jobDay.minusDays(1).atTime(LocalTime.of(18, 30)).atZone(ISTANBUL).toInstant();
        assertEquals(0, reminderScheduler.sendDueReminders(eveningBefore));

        Instant evening = jobDay.minusDays(1).atTime(LocalTime.of(19, 5)).atZone(ISTANBUL).toInstant();
        assertEquals(1, reminderScheduler.sendDueReminders(evening));
        assertEquals(0, reminderScheduler.sendDueReminders(evening.plusSeconds(300)));
        assertNotNull(jdbc.queryForObject("select evening_reminder_sent_at from seat_assignments "
                + "where job_assignment_id = ?", Object.class, assignmentId));
        assertNull(jdbc.queryForObject("select departure_reminder_sent_at from seat_assignments "
                + "where job_assignment_id = ?", Object.class, assignmentId));
        String eveningBody = jdbc.queryForObject("select body from notifications where recipient_id = ? "
                + "and channel = 'IN_APP' and category = 'TRANSPORT'", String.class, workerId);
        assertTrue(eveningBody.contains("06:40"), eveningBody);
        assertTrue(eveningBody.contains("Kadıköy İskele"), eveningBody);
        assertTrue(eveningBody.contains(PLATE), eveningBody);

        Instant tooEarly = departureAt().minusSeconds(61 * 60);
        assertEquals(0, reminderScheduler.sendDueReminders(tooEarly));
        Instant withinLead = departureAt().minusSeconds(30 * 60);
        assertEquals(1, reminderScheduler.sendDueReminders(withinLead));
        assertEquals(0, reminderScheduler.sendDueReminders(withinLead.plusSeconds(300)));
        assertEquals(2, jdbc.queryForObject("select count(*) from notifications where recipient_id = ? "
                + "and channel = 'IN_APP' and category = 'TRANSPORT'", Integer.class, workerId));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_id = ?",
                Integer.class, secondWorkerId));
    }

    @Test
    void aPlanRejectsAShuttleOnADirectionWithoutShuttleMethodAndAStopWithoutAnAddress() throws Exception {
        putPlan(employerId, jobId, "{\"outboundMethod\":\"SELF\",\"returnMethod\":\"NONE\",\"shuttles\":["
                + shuttleJson(null, null, 2) + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("everyShuttleOnAShuttleDirection"));

        putPlan(employerId, jobId, "{\"outboundMethod\":\"SHUTTLE\",\"returnMethod\":\"NONE\",\"shuttles\":["
                + "{\"direction\":\"OUTBOUND\",\"name\":\"Servis\",\"capacity\":2,\"stops\":["
                + "{\"scheduledAt\":\"" + departureAt() + "\"}]}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value(containsString("addressPresentForNewStop")));

        putPlan(employerId, jobId, "{\"outboundMethod\":\"SELF\",\"returnMethod\":\"SELF\",\"allowanceAmount\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("allowanceAmountConsistent"));
    }


    @Test
    void thePlateAndDriverShowOnlyOnTheShuttleTheWorkerPicked() throws Exception {
        putPlan(employerId, jobId, planWith(
                shuttleJson(null, "OUTBOUND", "Anadolu servisi", PLATE, 2, stopJson(null, "Kadıköy", departureAt(), true)),
                shuttleJson(null, "OUTBOUND", "Avrupa servisi", OTHER_PLATE, 2, stopJson(null, "Beşiktaş", departureAt(), true))))
                .andExpect(status().isOk());
        List<UUID> shuttles = shuttleIdsOf(jobId);
        UUID stopOfSecond = stopIdsOf(shuttles.get(1)).get(0);

        String body = selectSeat(workerId, assignmentId, shuttles.get(1), stopOfSecond)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shuttles[0].plate").doesNotExist())
                .andExpect(jsonPath("$.data.shuttles[0].driverPhone").doesNotExist())
                .andExpect(jsonPath("$.data.shuttles[1].plate").value(OTHER_PLATE))
                .andExpect(jsonPath("$.data.shuttles[1].driverPhone").value(DRIVER_PHONE))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains(PLATE), body);
    }

    @Test
    void removingOnlyAStopDropsItsSeatsAndTellsThatWorkerToPickAgain() throws Exception {
        putPlan(employerId, jobId, planWith(shuttleJson(null, "OUTBOUND", "Servis", PLATE, 4,
                stopJson(null, "Kadıköy", departureAt(), true),
                stopJson(null, "Üsküdar", departureAt().plusSeconds(600), true))))
                .andExpect(status().isOk());
        UUID shuttleId = shuttleIdsOf(jobId).get(0);
        List<UUID> stops = stopIdsOf(shuttleId);
        selectSeat(workerId, assignmentId, shuttleId, stops.get(0)).andExpect(status().isOk());
        selectSeat(secondWorkerId, secondAssignmentId, shuttleId, stops.get(1)).andExpect(status().isOk());
        jdbc.update("delete from notifications where recipient_id in (?, ?)", workerId, secondWorkerId);
        insertPushDevice(workerId);
        insertPushDevice(secondWorkerId);

        putPlan(employerId, jobId, planWith(shuttleJson(shuttleId, "OUTBOUND", "Servis", PLATE, 4,
                stopJson(stops.get(0), "Kadıköy", departureAt(), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shuttles[0].id").value(shuttleId.toString()))
                .andExpect(jsonPath("$.data.shuttles[0].stops", hasSize(1)))
                .andExpect(jsonPath("$.data.shuttles[0].seatsTaken").value(1));

        assertEquals(1, jdbc.queryForObject("select count(*) from shuttle_stops where id = ? and is_deleted = true",
                Integer.class, stops.get(1)));
        assertEquals(1, jdbc.queryForObject("select count(*) from seat_assignments where job_assignment_id = ? "
                + "and is_deleted = false", Integer.class, assignmentId));
        assertEquals(0, jdbc.queryForObject("select count(*) from seat_assignments where job_assignment_id = ? "
                + "and is_deleted = false", Integer.class, secondAssignmentId));
        assertEquals("PLAN_UPDATED", pushEventFor(workerId));
        assertEquals("SEAT_DROPPED", pushEventFor(secondWorkerId));
        String droppedBody = jdbc.queryForObject("select body from notifications where recipient_id = ? "
                + "and channel = 'IN_APP' and category = 'TRANSPORT'", String.class, secondWorkerId);
        assertTrue(droppedBody.contains("yeniden"), droppedBody);
    }

    @Test
    void anExistingShuttleCannotChangeDirectionAndAReschedulingResetsTheReminders() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(2);
        UUID stopId = stopIdOf(shuttleId);
        selectSeat(workerId, assignmentId, shuttleId, stopId).andExpect(status().isOk());
        jdbc.update("update seat_assignments set evening_reminder_sent_at = now(), departure_reminder_sent_at = now() "
                + "where job_assignment_id = ?", assignmentId);

        putPlan(employerId, jobId, "{\"outboundMethod\":\"SHUTTLE\",\"returnMethod\":\"SHUTTLE\",\"shuttles\":["
                + shuttleJson(shuttleId, "RETURN", "Anadolu servisi", PLATE, 2,
                stopJson(stopId, "Kadıköy İskele", departureAt(), false)) + "]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_SHUTTLE_DIRECTION_LOCKED"));
        assertEquals("OUTBOUND", jdbc.queryForObject("select direction from shuttles where id = ?", String.class, shuttleId));

        putPlan(employerId, jobId, planWith(shuttleJson(shuttleId, "OUTBOUND", "Anadolu servisi", PLATE, 2,
                stopJson(stopId, "Kadıköy İskele", departureAt().plusSeconds(1800), false))))
                .andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select evening_reminder_sent_at from seat_assignments "
                + "where job_assignment_id = ?", Object.class, assignmentId));
        assertNull(jdbc.queryForObject("select departure_reminder_sent_at from seat_assignments "
                + "where job_assignment_id = ?", Object.class, assignmentId));
        assertEquals(1, jdbc.queryForObject("select count(*) from seat_assignments where job_assignment_id = ? "
                + "and is_deleted = false", Integer.class, assignmentId));
    }

    @Test
    void replacingAStopsAddressSoftDeletesTheOldAddressRow() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(2);
        UUID stopId = stopIdOf(shuttleId);
        UUID oldAddressId = jdbc.queryForObject("select address_id from shuttle_stops where id = ?", UUID.class, stopId);

        putPlan(employerId, jobId, planWith(shuttleJson(shuttleId, "OUTBOUND", "Anadolu servisi", PLATE, 2,
                stopJson(stopId, "Kadıköy İskele", departureAt(), true))))
                .andExpect(status().isOk());

        UUID newAddressId = jdbc.queryForObject("select address_id from shuttle_stops where id = ?", UUID.class, stopId);
        assertFalse(oldAddressId.equals(newAddressId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select is_deleted from addresses where id = ?",
                Boolean.class, oldAddressId));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("select is_deleted from addresses where id = ?",
                Boolean.class, newAddressId));
    }

    @Test
    void aPendingAssignmentCannotPickASeatAndACancelledWorkerSeesOnlyDistrictLevelStops() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(2);
        UUID stopId = stopIdOf(shuttleId);
        selectSeat(workerId, assignmentId, shuttleId, stopId).andExpect(status().isOk());

        jdbc.update("update job_assignments set status = 'PENDING_CONFIRMATION' where id = ?", secondAssignmentId);
        selectSeat(secondWorkerId, secondAssignmentId, shuttleId, stopId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRANSPORT_ASSIGNMENT_NOT_ACTIVE"));

        jdbc.update("update job_assignments set status = 'CANCELLED' where id = ?", assignmentId);
        String body = mockMvc.perform(get("/job-assignments/{id}/transport", assignmentId)
                        .with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shuttles[0].plate").doesNotExist())
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].address.districtName").value("Kadıköy"))
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].address.components.neighborhood").value("Yıldız Mah."))
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].address.components.thoroughfare").doesNotExist())
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].address.components.buildingNumber").doesNotExist())
                .andExpect(jsonPath("$.data.shuttles[0].stops[0].address.position.latitude").value(41.05))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("Barbaros"), body);
        assertFalse(body.contains(DRIVER_PHONE), body);
    }

    @Test
    void thePushCarriesTheTransportTypeAndTheEvent() throws Exception {
        insertPushDevice(workerId);
        writePlanAndReadShuttleId(2);
        String payload = jdbc.queryForObject("select push_data_payload from notifications where recipient_id = ? "
                + "and channel = 'PUSH' and category = 'TRANSPORT'", String.class, workerId);
        Map<String, String> data = objectMapper.readValue(payload, new TypeReference<Map<String, String>>() {
        });
        assertEquals("TRANSPORT", data.get("type"));
        assertEquals("PLAN_UPDATED", data.get("event"));
        assertEquals(jobId.toString(), data.get("jobId"));
        assertEquals(assignmentId.toString(), data.get("jobAssignmentId"));
    }

    @Test
    void theAssignmentListsAreBuiltWithoutAQueryPerRowWhenTransportIsPresent() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(2);
        UUID stopId = stopIdOf(shuttleId);
        selectSeat(workerId, assignmentId, shuttleId, stopId).andExpect(status().isOk());
        selectSeat(secondWorkerId, secondAssignmentId, shuttleId, stopId).andExpect(status().isOk());

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).param("size", "1")
                            .with(user(principal(employerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].transport.seats", hasSize(1)));
            long statementsForOneRow = statistics.getPrepareStatementCount();
            long fetchesForOneRow = statistics.getEntityFetchCount();

            statistics.clear();
            mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).param("size", "2")
                            .with(user(principal(employerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.content[1].transport.shuttles[0].plate").value(PLATE));
            long statementsForTwoRows = statistics.getPrepareStatementCount();

            assertEquals(statementsForOneRow, statementsForTwoRows,
                    "ulaşım görünümü satır sayısıyla büyüyen sorgu atıyor (N+1)");
            assertEquals(0, fetchesForOneRow, "ulaşım görünümü satır başına lazy entity yüklüyor");
            assertEquals(0, statistics.getEntityFetchCount(), "ulaşım görünümü satır başına lazy entity yüklüyor");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test
    void twoWorkersRacingForTheLastSeatLeaveExactlyOneSeatAndOneRefusal() throws Exception {
        UUID shuttleId = writePlanAndReadShuttleId(1);
        UUID stopId = stopIdOf(shuttleId);
        widenTheSeatInsertWindow();
        try {
            OverlappingWrites overlap = new OverlappingWrites();
            List<String> outcomes = overlap.outcomesOf(
                    () -> selectThroughTheService(workerId, assignmentId, shuttleId, stopId, overlap),
                    () -> selectThroughTheService(secondWorkerId, secondAssignmentId, shuttleId, stopId, overlap));

            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count(), "exactly one seat must win: " + outcomes);
            assertEquals(1, outcomes.stream().filter("TRANSPORT_SHUTTLE_FULL"::equals).count(),
                    "the loser must be refused as full: " + outcomes);
            assertEquals(1, jdbc.queryForObject("select count(*) from seat_assignments where shuttle_id = ? "
                    + "and is_deleted = false", Integer.class, shuttleId));
        } finally {
            narrowTheSeatInsertWindow();
        }
    }

    @Test
    void theSameWorkerRacingOnTwoShuttlesKeepsOneSeatPerDirection() throws Exception {
        putPlan(employerId, jobId, planWith(
                shuttleJson(null, "OUTBOUND", "Anadolu servisi", PLATE, 2, stopJson(null, "Kadıköy", departureAt(), true)),
                shuttleJson(null, "OUTBOUND", "Avrupa servisi", OTHER_PLATE, 2, stopJson(null, "Beşiktaş", departureAt(), true))))
                .andExpect(status().isOk());
        List<UUID> shuttles = shuttleIdsOf(jobId);
        UUID firstStop = stopIdsOf(shuttles.get(0)).get(0);
        UUID secondStop = stopIdsOf(shuttles.get(1)).get(0);
        widenTheSeatInsertWindow();
        try {
            OverlappingWrites overlap = new OverlappingWrites();
            List<String> outcomes = overlap.outcomesOf(
                    () -> selectThroughTheService(workerId, assignmentId, shuttles.get(0), firstStop, overlap),
                    () -> selectThroughTheService(workerId, assignmentId, shuttles.get(1), secondStop, overlap));

            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count(), "exactly one seat must win: " + outcomes);
            assertEquals(1, outcomes.stream().filter("TRANSPORT_SEAT_ALREADY_TAKEN"::equals).count(),
                    "the loser must be refused by the per-direction index as 409: " + outcomes);
            assertEquals(1, jdbc.queryForObject("select count(*) from seat_assignments where job_assignment_id = ? "
                    + "and is_deleted = false", Integer.class, assignmentId));
        } finally {
            narrowTheSeatInsertWindow();
        }
    }

    private String selectThroughTheService(UUID actorId, UUID assignment, UUID shuttleId, UUID stopId,
                                           OverlappingWrites overlap) {
        UserPrincipal principal = principal(actorId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        try {
            transactions.execute(status -> {
                overlap.meet();
                return transportService.selectSeat(assignment, new SeatSelectionDto(shuttleId, stopId));
            });
            return "SUCCESS";
        } catch (ConflictException conflict) {
            return conflict.getErrorCode();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void widenTheSeatInsertWindow() {
        jdbc.execute("""
                create or replace function delay_seat_assignment_insert() returns trigger as $$
                begin
                    perform pg_sleep(0.5);
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbc.execute("create trigger delay_seat_assignment_insert before insert on seat_assignments "
                + "for each row execute function delay_seat_assignment_insert()");
    }

    private void narrowTheSeatInsertWindow() {
        jdbc.execute("drop trigger if exists delay_seat_assignment_insert on seat_assignments");
        jdbc.execute("drop function if exists delay_seat_assignment_insert()");
    }

    private void insertPushDevice(UUID userId) {
        jdbc.update("insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token) "
                + "values (?, now(), false, ?, 'ANDROID', 'transport-device', ?, ?)",
                UUID.randomUUID(), userId, UUID.randomUUID().toString(), "transport-token-" + userId);
    }

    private String pushEventFor(UUID recipient) throws Exception {
        String payload = jdbc.queryForObject("select push_data_payload from notifications where recipient_id = ? "
                + "and channel = 'PUSH' and category = 'TRANSPORT'", String.class, recipient);
        return objectMapper.readValue(payload, new TypeReference<Map<String, String>>() {
        }).get("event");
    }

    private UUID writePlanAndReadShuttleId(int capacity) throws Exception {
        putPlan(employerId, jobId, planJson(null, null, capacity)).andExpect(status().isOk());
        return jdbc.queryForObject("select s.id from shuttles s join transport_plans p on p.id = s.plan_id "
                + "where p.job_id = ? and s.is_deleted = false", UUID.class, jobId);
    }

    private UUID stopIdOf(UUID shuttleId) {
        return jdbc.queryForObject("select id from shuttle_stops where shuttle_id = ? and is_deleted = false",
                UUID.class, shuttleId);
    }

    private ResultActions putPlan(UUID actorId, UUID job, String body) throws Exception {
        return mockMvc.perform(put("/jobs/{jobId}/transport-plan", job)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions selectSeat(UUID actorId, UUID assignment, UUID shuttleId, UUID stopId) throws Exception {
        return mockMvc.perform(put("/job-assignments/{id}/transport/seat", assignment)
                .with(user(principal(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shuttleId\":\"" + shuttleId + "\",\"stopId\":\"" + stopId + "\"}"));
    }

    private Instant departureAt() {
        return jobDay.atTime(LocalTime.of(6, 40)).atZone(ISTANBUL).toInstant();
    }

    private String planJson(UUID shuttleId, UUID stopId, int capacity) {
        return "{\"outboundMethod\":\"SHUTTLE\",\"returnMethod\":\"CASH_ALLOWANCE\",\"allowanceAmount\":150.00,"
                + "\"allowanceNote\":\"İş çıkışı elden\",\"notes\":\"Servis kapıda bekler\","
                + "\"shuttles\":[" + shuttleJson(shuttleId, stopId, capacity) + "]}";
    }

    private String shuttleJson(UUID shuttleId, UUID stopId, int capacity) {
        return shuttleJson(shuttleId, "OUTBOUND", "Anadolu servisi", PLATE, capacity,
                stopJson(stopId, "Kadıköy İskele", departureAt(), stopId == null));
    }

    private String shuttleJson(UUID shuttleId, String direction, String name, String plate, int capacity,
                               String... stops) {
        String idField = shuttleId == null ? "" : "\"id\":\"" + shuttleId + "\",";
        return "{" + idField + "\"direction\":\"" + direction + "\",\"name\":\"" + name + "\",\"plate\":\"" + plate + "\","
                + "\"capacity\":" + capacity + ",\"driverName\":\"Ahmet Şoför\",\"driverPhone\":\"" + DRIVER_PHONE + "\","
                + "\"stops\":[" + String.join(",", stops) + "]}";
    }

    private String stopJson(UUID stopId, String label, Instant scheduledAt, boolean withAddress) {
        String idField = stopId == null ? "" : "\"id\":\"" + stopId + "\",";
        String address = withAddress ? ",\"address\":" + addressJson() : "";
        return "{" + idField + "\"label\":\"" + label + "\",\"scheduledAt\":\"" + scheduledAt + "\"" + address + "}";
    }

    private String planWith(String... shuttles) {
        return "{\"outboundMethod\":\"SHUTTLE\",\"returnMethod\":\"NONE\",\"shuttles\":["
                + String.join(",", shuttles) + "]}";
    }

    private List<UUID> shuttleIdsOf(UUID job) {
        return jdbc.queryForList("select s.id from shuttles s join transport_plans p on p.id = s.plan_id "
                + "where p.job_id = ? and s.is_deleted = false order by s.sort_order", UUID.class, job);
    }

    private List<UUID> stopIdsOf(UUID shuttleId) {
        return jdbc.queryForList("select id from shuttle_stops where shuttle_id = ? and is_deleted = false "
                + "order by sort_order", UUID.class, shuttleId);
    }

    private String addressJson() {
        return "{\"countryId\":\"" + countryId + "\",\"cityId\":\"" + cityId + "\",\"districtId\":\"" + districtId + "\","
                + "\"components\":{\"neighborhood\":\"Yıldız Mah.\",\"thoroughfare\":\"Barbaros Bulvarı\","
                + "\"buildingNumber\":\"45\",\"postalCode\":\"34353\"},"
                + "\"position\":{\"latitude\":41.0480,\"longitude\":29.0090,\"source\":\"USER_CONFIRMED_PIN\"}}";
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

    private void insertJob(UUID id, String jobTitle) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_id, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, start_date_time, end_date_time) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', ?, false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', now(), now() + interval '30 day')",
                id, employerId, categoryId, jobTitle, jobAddressId);
    }

    private void insertAssignment(UUID id, UUID worker) {
        jdbc.update("insert into job_assignments (id, job_id, worker_id, status, payment_status, work_hours, "
                + "is_deleted, created_at) values (?, ?, ?, 'CONFIRMED', 'HELD_IN_ESCROW', 0, false, now())",
                id, jobId, worker);
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)",
                id, "transport-" + id + "@example.com");
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, "transport-" + id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
