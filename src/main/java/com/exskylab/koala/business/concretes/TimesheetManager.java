package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.constants.TimesheetMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
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
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
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

    public TimesheetManager(WorkSessionDao workSessionDao, JobAssignmentService jobAssignmentService,
                            SecurityService securityService, AssignmentParticipants participants) {
        this.workSessionDao = workSessionDao;
        this.jobAssignmentService = jobAssignmentService;
        this.securityService = securityService;
        this.participants = participants;
    }

    @Override
    @Transactional
    public WorkSession checkIn(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude) {
        JobAssignment assignment = requireWorker(jobAssignmentId);

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
        session.setCheckInAt(Instant.now());
        session.setCheckInLatitude(latitude);
        session.setCheckInLongitude(longitude);

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
        requireWorker(jobAssignmentId);

        WorkSession session = workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(jobAssignmentId)
                .orElseThrow(() -> new ConflictException(TimesheetMessages.NOT_CHECKED_IN));

        Instant now = Instant.now();
        session.setCheckOutAt(now);
        session.setCheckOutLatitude(latitude);
        session.setCheckOutLongitude(longitude);
        session.setMinutes((int) Duration.between(session.getCheckInAt(), now).toMinutes());

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

        if (!participants.isParticipant(assignment, securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(TimesheetMessages.NOT_A_PARTICIPANT);
        }

        return workSessionDao.findAllByJobAssignmentIdOrderByCheckInAtAsc(jobAssignmentId);
    }

    private JobAssignment requireWorker(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        if (!assignment.getWorker().getId().equals(securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(TimesheetMessages.NOT_THE_WORKER);
        }
        return assignment;
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
