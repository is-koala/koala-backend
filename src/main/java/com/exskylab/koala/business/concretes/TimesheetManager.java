package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.constants.TimesheetMessages;
import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.core.helpers.TimesheetEventPolicy;
import com.exskylab.koala.dataAccess.WorkSessionDao;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.WorkSession;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class TimesheetManager implements TimesheetService {

    private static final Set<AssignmentStatus> OPEN_FOR_WORK =
            EnumSet.of(AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS);

    private final WorkSessionDao workSessionDao;
    private final JobAssignmentService jobAssignmentService;
    private final SecurityService securityService;
    private final AssignmentParticipants participants;
    private final TimesheetEventPolicy eventPolicy;
    private final Clock clock;

    public TimesheetManager(WorkSessionDao workSessionDao, JobAssignmentService jobAssignmentService,
                            SecurityService securityService, AssignmentParticipants participants,
                            TimesheetEventPolicy eventPolicy, Clock clock) {
        this.workSessionDao = workSessionDao;
        this.jobAssignmentService = jobAssignmentService;
        this.securityService = securityService;
        this.participants = participants;
        this.eventPolicy = eventPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WorkSession checkIn(UUID jobAssignmentId, CheckPointDto point) {
        Instant receivedAt = toDatabaseTimestamp(clock.instant());
        JobAssignment assignment = requireWorker(jobAssignmentId);
        workSessionDao.lockAssignment(jobAssignmentId);

        BigDecimal normalizedLatitude = normalizeCoordinate(point.getLatitude());
        BigDecimal normalizedLongitude = normalizeCoordinate(point.getLongitude());
        if (point.getClientEventId() != null) {
            WorkSession existing = workSessionDao
                    .findByJobAssignmentIdAndCheckInClientEventId(jobAssignmentId, point.getClientEventId())
                    .orElse(null);
            if (existing != null) {
                requireMatchingPayload(existing.getCheckInCapturedAt(), existing.getCheckInLatitude(),
                        existing.getCheckInLongitude(), point.getCapturedAt(),
                        normalizedLatitude, normalizedLongitude);
                return existing;
            }
        }

        if (!OPEN_FOR_WORK.contains(assignment.getStatus())) {
            throw new ConflictException(TimesheetMessages.ASSIGNMENT_NOT_ACTIVE);
        }

        if (assignment.getPaymentStatus() != PaymentStatus.HELD_IN_ESCROW) {
            log.warn("Check-in refused on assignment {}: money is not in escrow, payment status is {}",
                    jobAssignmentId, assignment.getPaymentStatus());
            throw new ConflictException(TimesheetMessages.PAYMENT_NOT_SECURED);
        }

        if (workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(jobAssignmentId).isPresent()) {
            throw new ConflictException(TimesheetMessages.ALREADY_CHECKED_IN);
        }

        WorkSession session = new WorkSession();
        session.setJobAssignment(assignment);
        session.setCheckInLatitude(point.getClientEventId() == null ? point.getLatitude() : normalizedLatitude);
        session.setCheckInLongitude(point.getClientEventId() == null ? point.getLongitude() : normalizedLongitude);
        if (point.getClientEventId() == null) {
            session.setCheckInAt(receivedAt);
        } else {
            TimesheetEventPolicy.Outcome outcome = eventPolicy.reconcile(point.getCapturedAt(), receivedAt,
                    assignment.getJob().getStartDateTime(), assignment.getJob().getEndDateTime(), null);
            session.setCheckInClientEventId(point.getClientEventId());
            session.setCheckInCapturedAt(point.getCapturedAt());
            session.setCheckInReceivedAt(receivedAt);
            session.setCheckInAt(toDatabaseTimestamp(outcome.effectiveAt()));
            session.setCheckInPlausibilityFlags(outcome.flags());
        }

        if (assignment.getStatus() != AssignmentStatus.IN_PROGRESS) {
            assignment.setStatus(AssignmentStatus.IN_PROGRESS);
            jobAssignmentService.save(assignment);
        }

        WorkSession saved = workSessionDao.save(session);
        log.info("Work session {} opened on assignment {}", saved.getId(), jobAssignmentId);
        return saved;
    }

    @Override
    @Transactional
    public WorkSession checkOut(UUID jobAssignmentId, CheckPointDto point) {
        Instant receivedAt = toDatabaseTimestamp(clock.instant());
        JobAssignment assignment = requireWorker(jobAssignmentId);
        workSessionDao.lockAssignment(jobAssignmentId);

        BigDecimal normalizedLatitude = normalizeCoordinate(point.getLatitude());
        BigDecimal normalizedLongitude = normalizeCoordinate(point.getLongitude());
        if (point.getClientEventId() != null) {
            WorkSession existing = workSessionDao
                    .findByJobAssignmentIdAndCheckOutClientEventId(jobAssignmentId, point.getClientEventId())
                    .orElse(null);
            if (existing != null) {
                requireMatchingPayload(existing.getCheckOutCapturedAt(), existing.getCheckOutLatitude(),
                        existing.getCheckOutLongitude(), point.getCapturedAt(),
                        normalizedLatitude, normalizedLongitude);
                return existing;
            }
        }

        WorkSession session = workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(jobAssignmentId)
                .orElseThrow(() -> new ConflictException(TimesheetMessages.NOT_CHECKED_IN));

        Instant effectiveAt = receivedAt;
        session.setCheckOutLatitude(point.getClientEventId() == null ? point.getLatitude() : normalizedLatitude);
        session.setCheckOutLongitude(point.getClientEventId() == null ? point.getLongitude() : normalizedLongitude);
        if (point.getClientEventId() != null) {
            TimesheetEventPolicy.Outcome outcome = eventPolicy.reconcile(point.getCapturedAt(), receivedAt,
                    assignment.getJob().getStartDateTime(), assignment.getJob().getEndDateTime(),
                    session.getCheckInAt());
            effectiveAt = toDatabaseTimestamp(outcome.effectiveAt());
            session.setCheckOutClientEventId(point.getClientEventId());
            session.setCheckOutCapturedAt(point.getCapturedAt());
            session.setCheckOutReceivedAt(receivedAt);
            session.setCheckOutPlausibilityFlags(outcome.flags());
        }
        session.setCheckOutAt(effectiveAt);
        session.setMinutes((int) Duration.between(session.getCheckInAt(), effectiveAt).toMinutes());

        WorkSession saved = workSessionDao.save(session);
        recalculateWorkHours(jobAssignmentId);

        log.info("Work session {} closed on assignment {} after {} minutes",
                saved.getId(), jobAssignmentId, saved.getMinutes());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkSession> getSessions(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        UUID actorId = securityService.getAuthenticatedUserId();
        if (!participants.isParticipant(assignment, actorId)) {
            throw Concealment.notFound(JobAssignmentMessages.NOT_FOUND, actorId, jobAssignmentId,
                    "not a participant of the assignment");
        }

        return workSessionDao.findAllByJobAssignmentIdOrderByCheckInAtAsc(jobAssignmentId);
    }

    private JobAssignment requireWorker(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        UUID actorId = securityService.getAuthenticatedUserId();
        if (!assignment.getWorker().getId().equals(actorId)) {
            throw Concealment.notFound(JobAssignmentMessages.NOT_FOUND, actorId, jobAssignmentId,
                    "not the worker of the assignment");
        }
        return assignment;
    }

    private void requireMatchingPayload(Instant storedCapturedAt, BigDecimal storedLatitude,
                                        BigDecimal storedLongitude, Instant capturedAt,
                                        BigDecimal latitude, BigDecimal longitude) {
        if (!Objects.equals(storedCapturedAt, capturedAt)
                || !coordinatesEqual(storedLatitude, latitude)
                || !coordinatesEqual(storedLongitude, longitude)) {
            throw new ConflictException(TimesheetMessages.IDEMPOTENCY_CONFLICT);
        }
    }

    private boolean coordinatesEqual(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private BigDecimal normalizeCoordinate(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(6, RoundingMode.HALF_UP);
    }

    private Instant toDatabaseTimestamp(Instant instant) {
        long roundedMicroseconds = (instant.getNano() + 500L) / 1_000L;
        return Instant.ofEpochSecond(instant.getEpochSecond(), roundedMicroseconds * 1_000L);
    }

    private void recalculateWorkHours(UUID jobAssignmentId) {
        int totalMinutes = workSessionDao.findAllByJobAssignmentIdOrderByCheckInAtAsc(jobAssignmentId).stream()
                .map(WorkSession::getMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        assignment.setWorkHours(BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                .intValue());
        jobAssignmentService.save(assignment);
    }
}
