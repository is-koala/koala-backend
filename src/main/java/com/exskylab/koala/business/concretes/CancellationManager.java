package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.CancellationMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.CancellationPolicy;
import com.exskylab.koala.dataAccess.CancellationDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CancellationManager implements CancellationService {

    private static final Set<AssignmentStatus> CANCELLABLE =
            EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION, AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS);

    private final Logger logger = LoggerFactory.getLogger(CancellationManager.class);

    private final CancellationDao cancellationDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final UserDao userDao;
    private final SecurityService securityService;
    private final AssignmentParticipants participants;
    private final CancellationPolicy policy;
    private final PayoutService payoutService;

    public CancellationManager(CancellationDao cancellationDao, JobAssignmentDao jobAssignmentDao,
                               UserDao userDao, SecurityService securityService,
                               AssignmentParticipants participants, CancellationPolicy policy,
                               PayoutService payoutService) {
        this.cancellationDao = cancellationDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.userDao = userDao;
        this.securityService = securityService;
        this.participants = participants;
        this.policy = policy;
        this.payoutService = payoutService;
    }

    @Override
    @Transactional
    public Cancellation cancelAssignment(UUID jobAssignmentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(CancellationMessages.REASON_REQUIRED);
        }

        JobAssignment assignment = jobAssignmentDao.findWithDetailsForPaymentById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(CancellationMessages.ASSIGNMENT_NOT_FOUND));

        UUID actorId = securityService.getAuthenticatedUserId();
        CancellationParty party = participants.partyOf(assignment, actorId)
                .orElseThrow(() -> new ResourceNotFoundException(CancellationMessages.NOT_A_PARTICIPANT));

        if (!CANCELLABLE.contains(assignment.getStatus())) {
            throw new IllegalStateException(CancellationMessages.NOT_CANCELLABLE);
        }

        if (cancellationDao.existsByJobAssignmentId(jobAssignmentId)) {
            throw new IllegalStateException(CancellationMessages.ALREADY_CANCELLED);
        }

        Duration notice = policy.noticeBefore(assignment.getJob().getStartDateTime(), Instant.now());
        boolean escrowHeld = assignment.getPaymentStatus() == PaymentStatus.HELD_IN_ESCROW;
        CancellationPolicy.Outcome outcome = policy.evaluate(
                party, escrowHeld ? assignment.getTotalCharged() : null, notice);

        Cancellation cancellation = new Cancellation();
        cancellation.setJobAssignment(assignment);
        cancellation.setCancelledBy(userDao.getReferenceById(actorId));
        cancellation.setCancelledByParty(party);
        cancellation.setReason(reason);
        cancellation.setNoticeMinutes(notice.toMinutes());
        cancellation.setFeeRate(outcome.feeRate());
        cancellation.setFeeAmount(outcome.feeAmount());
        cancellation.setRefundAmount(outcome.refundAmount());
        cancellation.setCancelledAt(Instant.now());

        assignment.setStatus(AssignmentStatus.CANCELLED);
        jobAssignmentDao.save(assignment);

        Cancellation saved = cancellationDao.save(cancellation);

        if (escrowHeld && outcome.refundAmount().signum() > 0) {
            payoutService.refundEmployer(jobAssignmentId, outcome.refundAmount(), reason);
        }

        logger.info("Assignment {} cancelled by {} ({}), notice {} min, fee {}, refund {}",
                jobAssignmentId, actorId, party, notice.toMinutes(),
                outcome.feeAmount(), outcome.refundAmount());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cancellation> getForAssignment(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentDao.findWithDetailsForPaymentById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(CancellationMessages.ASSIGNMENT_NOT_FOUND));

        if (!participants.isParticipant(assignment, securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(CancellationMessages.NOT_A_PARTICIPANT);
        }

        return cancellationDao.findByJobAssignmentId(jobAssignmentId);
    }
}
