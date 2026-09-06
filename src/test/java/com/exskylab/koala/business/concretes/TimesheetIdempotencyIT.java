package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import com.exskylab.koala.entities.WorkSession;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

class TimesheetIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private TimesheetService timesheetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SecurityService securityService;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID otherWorkerId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID otherAssignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(employerId, "timesheet-employer");
        insertUser(workerId, "timesheet-worker");
        insertUser(otherWorkerId, "timesheet-other-worker");
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, ?, 'Offline puantaj testi', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() - interval '1 hour', now() + interval '8 hours')
                """, jobId, employerId);
        insertAssignment(assignmentId, workerId);
        insertAssignment(otherAssignmentId, otherWorkerId);
        when(securityService.getAuthenticatedUserId()).thenReturn(workerId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from work_sessions where job_assignment_id in (?, ?)",
                assignmentId, otherAssignmentId);
        jdbcTemplate.update("delete from job_assignments where id in (?, ?)", assignmentId, otherAssignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
        jdbcTemplate.update("delete from users where id in (?, ?, ?)", employerId, workerId, otherWorkerId);
    }

    @Test
    void concurrentRetriesOfTheSameEventProduceOneSessionAndOneResult() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant capturedAt = Instant.now().minusSeconds(60);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<WorkSession> first = executor.submit(() -> checkInAfter(ready, start, eventId, capturedAt));
            Future<WorkSession> second = executor.submit(() -> checkInAfter(ready, start, eventId, capturedAt));
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            WorkSession firstResult = first.get(10, TimeUnit.SECONDS);
            WorkSession secondResult = second.get(10, TimeUnit.SECONDS);
            assertSameResponse(firstResult, secondResult);
        }

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from work_sessions where job_assignment_id = ?", Integer.class, assignmentId);
        assertEquals(1, count);
    }

    @Test
    void retryPreservesAndMatchesTheExactNanosecondCapturedAt() {
        UUID eventId = UUID.randomUUID();
        Instant capturedAt = Instant.now().minusSeconds(60).with(ChronoField.NANO_OF_SECOND,
                123_456_789);

        WorkSession first = timesheetService.checkIn(assignmentId,
                new CheckPointDto(new BigDecimal("41.015137"), new BigDecimal("28.979530"), eventId, capturedAt));
        assertEquals(capturedAt, first.getCheckInCapturedAt());
        WorkSession retry = timesheetService.checkIn(assignmentId,
                new CheckPointDto(new BigDecimal("41.015137"), new BigDecimal("28.979530"), eventId, capturedAt));

        assertSameResponse(first, retry);
    }

    @Test
    void checkOutRetryPreservesAndMatchesTheExactNanosecondCapturedAt() {
        Instant checkInCapturedAt = Instant.now().minusSeconds(120)
                .with(ChronoField.NANO_OF_SECOND, 123_456_789);
        timesheetService.checkIn(assignmentId,
                new CheckPointDto(null, null, UUID.randomUUID(), checkInCapturedAt));

        UUID checkOutEventId = UUID.randomUUID();
        Instant checkOutCapturedAt = checkInCapturedAt.plusSeconds(60).plusNanos(1);
        CheckPointDto checkOutPoint = new CheckPointDto(null, null, checkOutEventId, checkOutCapturedAt);
        WorkSession first = timesheetService.checkOut(assignmentId, checkOutPoint);
        WorkSession retry = timesheetService.checkOut(assignmentId, checkOutPoint);

        assertEquals(checkOutCapturedAt, first.getCheckOutCapturedAt());
        assertSameResponse(first, retry);
    }

    @Test
    void theSameEventIdentityOnAnotherWorkersAssignmentDoesNotRevealTheFirstResult() {
        UUID eventId = UUID.randomUUID();
        Instant capturedAt = Instant.now().minusSeconds(60);
        WorkSession first = timesheetService.checkIn(assignmentId,
                new CheckPointDto(null, null, eventId, capturedAt));

        when(securityService.getAuthenticatedUserId()).thenReturn(otherWorkerId);
        WorkSession second = timesheetService.checkIn(otherAssignmentId,
                new CheckPointDto(null, null, eventId, capturedAt));

        assertNotEquals(first.getId(), second.getId());
        assertEquals(otherAssignmentId, second.getJobAssignment().getId());
    }

    private WorkSession checkInAfter(CountDownLatch ready, CountDownLatch start,
                                     UUID eventId, Instant capturedAt) throws Exception {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        return timesheetService.checkIn(assignmentId, new CheckPointDto(new BigDecimal("41.015137"),
                new BigDecimal("28.979530"), eventId, capturedAt));
    }

    private void assertSameResponse(WorkSession expected, WorkSession actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCheckInAt(), actual.getCheckInAt());
        assertEquals(expected.getCheckOutAt(), actual.getCheckOutAt());
        assertEquals(expected.getCheckInClientEventId(), actual.getCheckInClientEventId());
        assertEquals(expected.getCheckInCapturedAt(), actual.getCheckInCapturedAt());
        assertEquals(expected.getCheckInReceivedAt(), actual.getCheckInReceivedAt());
        assertEquals(expected.getCheckInPlausibilityFlags(), actual.getCheckInPlausibilityFlags());
        assertEquals(expected.getCheckOutClientEventId(), actual.getCheckOutClientEventId());
        assertEquals(expected.getCheckOutCapturedAt(), actual.getCheckOutCapturedAt());
        assertEquals(expected.getCheckOutReceivedAt(), actual.getCheckOutReceivedAt());
        assertEquals(expected.getCheckOutPlausibilityFlags(), actual.getCheckOutPlausibilityFlags());
        assertEquals(expected.getCheckInLatitude(), actual.getCheckInLatitude());
        assertEquals(expected.getCheckInLongitude(), actual.getCheckInLongitude());
        assertEquals(expected.getCheckOutLatitude(), actual.getCheckOutLatitude());
        assertEquals(expected.getCheckOutLongitude(), actual.getCheckOutLongitude());
        assertEquals(expected.getMinutes(), actual.getMinutes());
    }

    private void insertAssignment(UUID id, UUID assignedWorkerId) {
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status, work_hours)
                values (?, now(), false, ?, ?, 'CONFIRMED', 'HELD_IN_ESCROW', 0)
                """, id, jobId, assignedWorkerId);
    }

    private void insertUser(UUID id, String prefix) {
        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified)
                values (?, now(), false, ?, 'test', false, true, false, false)
                """, id, prefix + "-" + id + "@koala.test");
    }
}
