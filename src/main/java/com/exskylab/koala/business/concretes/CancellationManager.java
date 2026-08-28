package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.CancellationMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.CancellationPolicy;
import com.exskylab.koala.dataAccess.CancellationDao;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CancellationManager implements CancellationService {

    private static final Set<AssignmentStatus> CANCELLABLE =
            EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION, AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS);

    private final CancellationDao cancellationDao;
    private final JobAssignmentService jobAssignmentService;
    private final UserService userService;
    private final SecurityService securityService;
    private final AssignmentParticipants participants;
    private final CancellationPolicy policy;
    private final PayoutService payoutService;
    private final ActivityNotificationService activityNotificationService;

    public CancellationManager(CancellationDao cancellationDao, JobAssignmentService jobAssignmentService,
                               UserService userService, SecurityService securityService,
                               AssignmentParticipants participants, CancellationPolicy policy,
                               PayoutService payoutService,
                               ActivityNotificationService activityNotificationService) {
        this.cancellationDao = cancellationDao;
        this.jobAssignmentService = jobAssignmentService;
        this.userService = userService;
        this.securityService = securityService;
        this.participants = participants;
        this.policy = policy;
        this.payoutService = payoutService;
        this.activityNotificationService = activityNotificationService;
    }

    @Override
    @Transactional
    public Cancellation cancelAssignment(UUID jobAssignmentId, String reason) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        UUID actorId = securityService.getAuthenticatedUserId();
        CancellationParty party = participants.partyOf(assignment, actorId)
                .orElseThrow(() -> new ResourceNotFoundException(CancellationMessages.NOT_A_PARTICIPANT));

        if (!CANCELLABLE.contains(assignment.getStatus())) {
            throw new ConflictException(CancellationMessages.NOT_CANCELLABLE);
        }

        if (cancellationDao.existsByJobAssignmentId(jobAssignmentId)) {
            throw new ConflictException(CancellationMessages.ALREADY_CANCELLED);
        }

        Duration notice = policy.noticeBefore(assignment.getJob().getStartDateTime(), Instant.now());
        boolean escrowHeld = assignment.getPaymentStatus() == PaymentStatus.HELD_IN_ESCROW;
        CancellationPolicy.Outcome outcome = policy.evaluate(
                party, escrowHeld ? assignment.getTotalCharged() : null, notice);

        Cancellation cancellation = new Cancellation();
        cancellation.setJobAssignment(assignment);
        cancellation.setCancelledBy(userService.getReference(actorId));
        cancellation.setCancelledByParty(party);
        cancellation.setReason(reason);
        cancellation.setNoticeMinutes(notice.toMinutes());
        cancellation.setFeeRate(outcome.feeRate());
        cancellation.setFeeAmount(outcome.feeAmount());
        cancellation.setRefundAmount(outcome.refundAmount());
        cancellation.setCancelledAt(Instant.now());

        assignment.setStatus(AssignmentStatus.CANCELLED);
        jobAssignmentService.save(assignment);

        Cancellation saved = cancellationDao.save(cancellation);

        if (escrowHeld && outcome.refundAmount().signum() > 0) {
            payoutService.refundEmployer(jobAssignmentId, outcome.refundAmount(), reason);
        }

        log.info("Assignment {} cancelled by {} ({}), notice {} min, fee {}, refund {}",
                jobAssignmentId, actorId, party, notice.toMinutes(),
                outcome.feeAmount(), outcome.refundAmount());

        activityNotificationService.assignmentCancelled(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cancellation> getForAssignment(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        if (!participants.isParticipant(assignment, securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(CancellationMessages.NOT_A_PARTICIPANT);
        }

        return cancellationDao.findByJobAssignmentId(jobAssignmentId);
    }
}
