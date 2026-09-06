package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.TimesheetEventPolicy;
import com.exskylab.koala.core.properties.TimesheetProperties;
import com.exskylab.koala.dataAccess.WorkSessionDao;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.WorkSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimesheetManagerOfflineEventTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-09-06T12:00:00Z");

    private WorkSessionDao workSessionDao;
    private JobAssignmentService jobAssignmentService;
    private SecurityService securityService;
    private TimesheetManager manager;
    private JobAssignment assignment;

    @BeforeEach
    void setUp() {
        workSessionDao = mock(WorkSessionDao.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        securityService = mock(SecurityService.class);
        AssignmentParticipants participants = new AssignmentParticipants(mock(CompanyContactService.class));
        manager = new TimesheetManager(workSessionDao, jobAssignmentService, securityService, participants,
                new TimesheetEventPolicy(new TimesheetProperties()), Clock.fixed(RECEIVED_AT, ZoneOffset.UTC));

        User worker = new User();
        worker.setId(UUID.randomUUID());
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setStartDateTime(LocalDateTime.parse("2026-09-06T09:00:00"));
        job.setEndDateTime(LocalDateTime.parse("2026-09-06T18:00:00"));
        assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setWorker(worker);
        assignment.setJob(job);
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.HELD_IN_ESCROW);

        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(jobAssignmentService.getDetailedById(assignment.getId())).thenReturn(assignment);
        when(workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(assignment.getId())).thenReturn(Optional.empty());
        when(workSessionDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workSessionDao.findAllByJobAssignmentIdOrderByCheckInAtAsc(assignment.getId())).thenReturn(List.of());
    }

    @Test
    void aNewCheckInStoresRawReceiptAndEffectiveTimesSeparately() {
        UUID eventId = UUID.randomUUID();
        Instant capturedAt = Instant.parse("2026-09-06T10:00:00Z");

        WorkSession session = manager.checkIn(assignment.getId(), new BigDecimal("41.0151374"),
                new BigDecimal("28.9795304"), eventId, capturedAt);

        assertEquals(eventId, session.getCheckInClientEventId());
        assertEquals(capturedAt, session.getCheckInCapturedAt());
        assertEquals(RECEIVED_AT, session.getCheckInReceivedAt());
        assertEquals(capturedAt, session.getCheckInAt());
        assertEquals(new BigDecimal("41.015137"), session.getCheckInLatitude());
        assertEquals(new BigDecimal("28.979530"), session.getCheckInLongitude());
        assertEquals(java.util.Set.of(), session.getCheckInPlausibilityFlags());
        verify(workSessionDao).lockAssignment(assignment.getId());
    }

    @Test
    void retryingTheSameNormalizedCheckInReturnsTheOriginalResult() {
        UUID eventId = UUID.randomUUID();
        Instant capturedAt = Instant.parse("2026-09-06T10:00:00Z");
        WorkSession original = checkInEvent(eventId, capturedAt, "41.015137", "28.979530");
        when(workSessionDao.findByJobAssignmentIdAndCheckInClientEventId(assignment.getId(), eventId))
                .thenReturn(Optional.of(original));

        WorkSession result = manager.checkIn(assignment.getId(), new BigDecimal("41.0151374"),
                new BigDecimal("28.9795304"), eventId, capturedAt);

        assertEquals(original, result);
        verify(workSessionDao, never()).save(any());
    }

    @Test
    void reusingACheckInIdentityWithDifferentPayloadIsRejected() {
        UUID eventId = UUID.randomUUID();
        Instant capturedAt = Instant.parse("2026-09-06T10:00:00Z");
        WorkSession original = checkInEvent(eventId, capturedAt, "41.015137", "28.979530");
        when(workSessionDao.findByJobAssignmentIdAndCheckInClientEventId(assignment.getId(), eventId))
                .thenReturn(Optional.of(original));

        assertThrows(ConflictException.class, () -> manager.checkIn(assignment.getId(),
                new BigDecimal("41.015138"), new BigDecimal("28.979530"), eventId, capturedAt));
        verify(workSessionDao, never()).save(any());
    }

    @Test
    void retryingACheckOutFindsTheAlreadyClosedSession() {
        UUID eventId = UUID.randomUUID();
        Instant capturedAt = Instant.parse("2026-09-06T11:00:00Z");
        WorkSession closed = checkInEvent(UUID.randomUUID(), Instant.parse("2026-09-06T10:00:00Z"), null, null);
        closed.setCheckOutClientEventId(eventId);
        closed.setCheckOutCapturedAt(capturedAt);
        closed.setCheckOutLatitude(new BigDecimal("41.015137"));
        closed.setCheckOutLongitude(new BigDecimal("28.979530"));
        closed.setCheckOutAt(capturedAt);
        when(workSessionDao.findByJobAssignmentIdAndCheckOutClientEventId(assignment.getId(), eventId))
                .thenReturn(Optional.of(closed));

        WorkSession result = manager.checkOut(assignment.getId(), new BigDecimal("41.015137"),
                new BigDecimal("28.979530"), eventId, capturedAt);

        assertEquals(closed, result);
        verify(workSessionDao, never()).findByJobAssignmentIdAndCheckOutAtIsNull(any());
        verify(workSessionDao, never()).save(any());
    }

    @Test
    void aNonMonotonicCheckOutIsFlaggedAndCannotProduceNegativeMinutes() {
        WorkSession open = checkInEvent(UUID.randomUUID(), Instant.parse("2026-09-06T11:00:00Z"), null, null);
        open.setCheckInAt(Instant.parse("2026-09-06T11:00:00Z"));
        when(workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(assignment.getId())).thenReturn(Optional.of(open));
        when(workSessionDao.findAllByJobAssignmentIdOrderByCheckInAtAsc(assignment.getId())).thenReturn(List.of(open));

        WorkSession closed = manager.checkOut(assignment.getId(), null, null, UUID.randomUUID(),
                Instant.parse("2026-09-06T10:00:00Z"));

        assertEquals(open.getCheckInAt(), closed.getCheckOutAt());
        assertEquals(0, closed.getMinutes());
        assertEquals(java.util.Set.of(TimesheetPlausibilityFlag.NON_MONOTONIC),
                closed.getCheckOutPlausibilityFlags());
    }

    private WorkSession checkInEvent(UUID eventId, Instant capturedAt, String latitude, String longitude) {
        WorkSession session = new WorkSession();
        session.setId(UUID.randomUUID());
        session.setJobAssignment(assignment);
        session.setCheckInClientEventId(eventId);
        session.setCheckInCapturedAt(capturedAt);
        session.setCheckInLatitude(latitude == null ? null : new BigDecimal(latitude));
        session.setCheckInLongitude(longitude == null ? null : new BigDecimal(longitude));
        session.setCheckInAt(capturedAt);
        return session;
    }
}
