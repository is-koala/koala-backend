package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.StaffPayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import java.util.Set;
import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class DisputeManager implements DisputeService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "status");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");


    static final List<DisputeStatus> ACTIVE = List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW);

    private final PageableSanitizer pageableSanitizer;
    private final DisputeDao disputeDao;
    private final JobAssignmentService jobAssignmentService;
    private final UserService userService;
    private final SecurityService securityService;
    private final AssignmentParticipants participants;
    private final StaffPayoutService staffPayoutService;
    private final ActivityNotificationService activityNotificationService;

    public DisputeManager(DisputeDao disputeDao, JobAssignmentService jobAssignmentService, UserService userService,
                          SecurityService securityService, AssignmentParticipants participants,
                          StaffPayoutService staffPayoutService, ActivityNotificationService activityNotificationService,
                                PageableSanitizer pageableSanitizer) {
        this.disputeDao = disputeDao;
        this.jobAssignmentService = jobAssignmentService;
        this.userService = userService;
        this.securityService = securityService;
        this.participants = participants;
        this.staffPayoutService = staffPayoutService;
        this.activityNotificationService = activityNotificationService;
            this.pageableSanitizer = pageableSanitizer;
    }

    @Override
    @Transactional
    public Dispute openDispute(UUID jobAssignmentId, String reason) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        UUID actorId = securityService.getAuthenticatedUserId();
        if (!participants.isParticipant(assignment, actorId)) {
            throw new ResourceNotFoundException(DisputeMessages.NOT_A_PARTICIPANT);
        }

        if (assignment.getPaymentStatus() != PaymentStatus.HELD_IN_ESCROW) {
            throw new ConflictException(DisputeMessages.NOTHING_TO_DISPUTE);
        }

        if (disputeDao.existsByJobAssignmentIdAndStatusIn(jobAssignmentId, ACTIVE)) {
            throw new ConflictException(DisputeMessages.ALREADY_OPEN);
        }

        Dispute dispute = new Dispute();
        dispute.setJobAssignment(assignment);
        dispute.setOpenedBy(userService.getReference(actorId));
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setReason(reason);

        Dispute saved = disputeDao.save(dispute);
        log.info("Dispute {} opened on assignment {} by user {}", saved.getId(), jobAssignmentId, actorId);

        activityNotificationService.disputeOpened(saved);
        return saved;
    }

    @Override
    @Transactional
    public Dispute resolveDispute(StaffPrincipal actor, UUID disputeId, DisputeResolution resolution, String note) {
        Dispute dispute = disputeDao.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.NOT_FOUND));

        UUID assignmentId = dispute.getJobAssignment().getId();

        if (resolution.movesMoney()) {
            staffPayoutService.requirePermission(actor, resolution.getMoneyPermission(), assignmentId);
        }

        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new ConflictException(DisputeMessages.ALREADY_RESOLVED);
        }

        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolution(resolution);
        dispute.setResolutionNote(note);
        if (actor == null) {
            dispute.setResolvedBy(securityService.getAuthenticatedUser());
        }
        dispute.setResolvedAt(Instant.now());

        Dispute saved = disputeDao.save(dispute);

        switch (resolution) {
            case RELEASE_TO_WORKER -> staffPayoutService.releaseToWorker(actor, assignmentId, note);
            case REFUND_EMPLOYER -> staffPayoutService.refundEmployer(actor, assignmentId, null, note);
            case DISMISSED -> log.info("Dispute {} dismissed, escrow untouched.", disputeId);
        }

        log.info("Dispute {} resolved as {} on assignment {}", disputeId, resolution, assignmentId);

        activityNotificationService.disputeResolved(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispute> getForAssignment(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        if (!participants.isParticipant(assignment, securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(DisputeMessages.NOT_A_PARTICIPANT);
        }

        return disputeDao.findAllByJobAssignmentIdOrderByCreatedAtDesc(jobAssignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Dispute> getPending(Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);
        return disputeDao.findAllByStatusIn(ACTIVE,
                pageableSanitizer.sanitize(requested, SORTABLE, DEFAULT_SORT));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveDispute(UUID jobAssignmentId) {
        return disputeDao.existsByJobAssignmentIdAndStatusIn(jobAssignmentId, ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveDisputeForUser(UUID userId) {
        return disputeDao.existsActiveForUser(userId, ACTIVE);
    }
}
