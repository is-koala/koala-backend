package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DisputeManager implements DisputeService {

    static final List<DisputeStatus> ACTIVE = List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW);

    private final Logger logger = LoggerFactory.getLogger(DisputeManager.class);

    private final DisputeDao disputeDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final UserDao userDao;
    private final SecurityService securityService;
    private final AssignmentParticipants participants;
    private final PayoutService payoutService;

    public DisputeManager(DisputeDao disputeDao, JobAssignmentDao jobAssignmentDao, UserDao userDao,
                          SecurityService securityService, AssignmentParticipants participants,
                          PayoutService payoutService) {
        this.disputeDao = disputeDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.userDao = userDao;
        this.securityService = securityService;
        this.participants = participants;
        this.payoutService = payoutService;
    }

    @Override
    @Transactional
    public Dispute openDispute(UUID jobAssignmentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(DisputeMessages.REASON_REQUIRED);
        }

        JobAssignment assignment = jobAssignmentDao.findWithDetailsForPaymentById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.ASSIGNMENT_NOT_FOUND));

        UUID actorId = securityService.getAuthenticatedUserId();
        if (!participants.isParticipant(assignment, actorId)) {
            throw new ResourceNotFoundException(DisputeMessages.NOT_A_PARTICIPANT);
        }

        if (assignment.getPaymentStatus() != PaymentStatus.HELD_IN_ESCROW) {
            throw new IllegalStateException(DisputeMessages.NOTHING_TO_DISPUTE);
        }

        if (disputeDao.existsByJobAssignmentIdAndStatusIn(jobAssignmentId, ACTIVE)) {
            throw new IllegalStateException(DisputeMessages.ALREADY_OPEN);
        }

        Dispute dispute = new Dispute();
        dispute.setJobAssignment(assignment);
        dispute.setOpenedBy(userDao.getReferenceById(actorId));
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setReason(reason);

        Dispute saved = disputeDao.save(dispute);
        logger.info("Dispute {} opened on assignment {} by user {}", saved.getId(), jobAssignmentId, actorId);
        return saved;
    }

    @Override
    @Transactional
    public Dispute resolveDispute(UUID disputeId, DisputeResolution resolution, String note) {
        if (resolution == null) {
            throw new IllegalArgumentException(DisputeMessages.RESOLUTION_REQUIRED);
        }

        Dispute dispute = disputeDao.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.NOT_FOUND));

        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new IllegalStateException(DisputeMessages.ALREADY_RESOLVED);
        }

        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolution(resolution);
        dispute.setResolutionNote(note);
        dispute.setResolvedBy(securityService.getAuthenticatedUser());
        dispute.setResolvedAt(Instant.now());

        Dispute saved = disputeDao.save(dispute);
        UUID assignmentId = dispute.getJobAssignment().getId();

        switch (resolution) {
            case RELEASE_TO_WORKER -> payoutService.releaseToWorker(assignmentId);
            case REFUND_EMPLOYER -> payoutService.refundEmployer(assignmentId, null, note);
            case DISMISSED -> logger.info("Dispute {} dismissed, escrow untouched.", disputeId);
        }

        logger.info("Dispute {} resolved as {} on assignment {}", disputeId, resolution, assignmentId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispute> getForAssignment(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentDao.findWithDetailsForPaymentById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.ASSIGNMENT_NOT_FOUND));

        if (!participants.isParticipant(assignment, securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(DisputeMessages.NOT_A_PARTICIPANT);
        }

        return disputeDao.findAllByJobAssignmentIdOrderByCreatedAtDesc(jobAssignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Dispute> getPending(Pageable pageable) {
        return disputeDao.findAllByStatusIn(ACTIVE, pageable);
    }
}
