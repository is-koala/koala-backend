package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.constants.TimesheetMessages;
import com.exskylab.koala.core.exceptions.ValidationException;
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
    public WorkSession checkIn(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude) {
        return checkIn(jobAssignmentId, latitude, longitude, null, null);
    }

    @Override
    @Transactional
    public WorkSession checkIn(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude,
                               UUID clientEventId, Instant capturedAt) {
        JobAssignment assignment = requireWorker(jobAssignmentId);
        requireCompleteEventIdentity(clientEventId, capturedAt);
        workSessionDao.lockAssignment(jobAssignmentId);

        BigDecimal normalizedLatitude = normalizeCoordinate(latitude);
        BigDecimal normalizedLongitude = normalizeCoordinate(longitude);
        if (clientEventId != null) {
            WorkSession existing = workSessionDao
                    .findByJobAssignmentIdAndCheckInClientEventId(jobAssignmentId, clientEventId)
                    .orElse(null);
            if (existing != null) {
                requireMatchingPayload(existing.getCheckInCapturedAt(), existing.getCheckInLatitude(),
                        existing.getCheckInLongitude(), capturedAt, normalizedLatitude, normalizedLongitude);
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
        Instant receivedAt = clock.instant();
        session.setCheckInLatitude(clientEventId == null ? latitude : normalizedLatitude);
        session.setCheckInLongitude(clientEventId == null ? longitude : normalizedLongitude);
        if (clientEventId == null) {
            session.setCheckInAt(receivedAt);
        } else {
            TimesheetEventPolicy.Outcome outcome = eventPolicy.reconcile(capturedAt, receivedAt,
                    assignment.getJob().getStartDateTime(), assignment.getJob().getEndDateTime(), null);
            session.setCheckInClientEventId(clientEventId);
            session.setCheckInCapturedAt(capturedAt);
            session.setCheckInReceivedAt(receivedAt);
            session.setCheckInAt(outcome.effectiveAt());
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
    public WorkSession checkOut(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude) {
        return checkOut(jobAssignmentId, latitude, longitude, null, null);
    }

    @Override
    @Transactional
    public WorkSession checkOut(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude,
                                UUID clientEventId, Instant capturedAt) {
        JobAssignment assignment = requireWorker(jobAssignmentId);
        requireCompleteEventIdentity(clientEventId, capturedAt);
        workSessionDao.lockAssignment(jobAssignmentId);

        BigDecimal normalizedLatitude = normalizeCoordinate(latitude);
        BigDecimal normalizedLongitude = normalizeCoordinate(longitude);
        if (clientEventId != null) {
            WorkSession existing = workSessionDao
                    .findByJobAssignmentIdAndCheckOutClientEventId(jobAssignmentId, clientEventId)
                    .orElse(null);
            if (existing != null) {
                requireMatchingPayload(existing.getCheckOutCapturedAt(), existing.getCheckOutLatitude(),
                        existing.getCheckOutLongitude(), capturedAt, normalizedLatitude, normalizedLongitude);
                return existing;
            }
        }

        WorkSession session = workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(jobAssignmentId)
                .orElseThrow(() -> new ConflictException(TimesheetMessages.NOT_CHECKED_IN));

        Instant receivedAt = clock.instant();
        Instant effectiveAt = receivedAt;
        session.setCheckOutLatitude(clientEventId == null ? latitude : normalizedLatitude);
        session.setCheckOutLongitude(clientEventId == null ? longitude : normalizedLongitude);
        if (clientEventId != null) {
            TimesheetEventPolicy.Outcome outcome = eventPolicy.reconcile(capturedAt, receivedAt,
                    assignment.getJob().getStartDateTime(), assignment.getJob().getEndDateTime(),
                    session.getCheckInAt());
            effectiveAt = outcome.effectiveAt();
            session.setCheckOutClientEventId(clientEventId);
            session.setCheckOutCapturedAt(capturedAt);
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

    private void requireCompleteEventIdentity(UUID clientEventId, Instant capturedAt) {
        if ((clientEventId == null) != (capturedAt == null)) {
            throw new ValidationException(TimesheetMessages.CLIENT_EVENT_PAIR);
        }
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
