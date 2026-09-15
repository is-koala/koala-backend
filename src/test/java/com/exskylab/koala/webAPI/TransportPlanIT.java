package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.schedulers.TransportReminderScheduler;
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
    private static final String DRIVER_PHONE = "+905551112233";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransportReminderScheduler reminderScheduler;

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
        jdbc.update("delete from job_assignments where job_id in (?, ?)", jobId, otherJobId);
        jdbc.update("delete from jobs where id in (?, ?)", jobId, otherJobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", jobAddressId);
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
        String idField = shuttleId == null ? "" : "\"id\":\"" + shuttleId + "\",";
        String stopIdField = stopId == null ? "" : "\"id\":\"" + stopId + "\",";
        String address = stopId == null ? ",\"address\":" + addressJson() : "";
        return "{" + idField + "\"direction\":\"OUTBOUND\",\"name\":\"Anadolu servisi\",\"plate\":\"" + PLATE + "\","
                + "\"capacity\":" + capacity + ",\"driverName\":\"Ahmet Şoför\",\"driverPhone\":\"" + DRIVER_PHONE + "\","
                + "\"stops\":[{" + stopIdField + "\"label\":\"Kadıköy İskele\",\"scheduledAt\":\"" + departureAt() + "\""
                + address + "}]}";
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
