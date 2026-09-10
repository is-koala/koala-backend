package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.StaffPayoutService;
import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.security.StaffAuditContext;
import com.exskylab.koala.core.helpers.Checksums;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.core.helpers.StorageRollbackCleanup;
import com.exskylab.koala.core.helpers.UploadValidator;
import com.exskylab.koala.core.dtos.dispute.response.StaffDisputeEvidenceDto;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.properties.UploadProperties;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.dataAccess.DisputeEvidenceDao;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final DisputeEvidenceDao disputeEvidenceDao;
    private final StorageService storageService;
    private final UploadValidator uploadValidator;
    private final UploadProperties uploadProperties;
    private final JobAssignmentService jobAssignmentService;
    private final UserService userService;
    private final SecurityService securityService;
    private final AssignmentParticipants participants;
    private final StaffPayoutService staffPayoutService;
    private final StaffAccountService staffAccountService;
    private final ActivityNotificationService activityNotificationService;
    private final DisputeMapper disputeMapper;

    public DisputeManager(DisputeDao disputeDao, DisputeEvidenceDao disputeEvidenceDao, StorageService storageService,
                          UploadValidator uploadValidator, UploadProperties uploadProperties,
                          JobAssignmentService jobAssignmentService, UserService userService,
                          SecurityService securityService, AssignmentParticipants participants,
                          StaffPayoutService staffPayoutService, StaffAccountService staffAccountService,
                          ActivityNotificationService activityNotificationService,
                          PageableSanitizer pageableSanitizer, DisputeMapper disputeMapper) {
        this.disputeDao = disputeDao;
        this.disputeEvidenceDao = disputeEvidenceDao;
        this.storageService = storageService;
        this.uploadValidator = uploadValidator;
        this.uploadProperties = uploadProperties;
        this.jobAssignmentService = jobAssignmentService;
        this.userService = userService;
        this.securityService = securityService;
        this.participants = participants;
        this.staffPayoutService = staffPayoutService;
        this.staffAccountService = staffAccountService;
        this.activityNotificationService = activityNotificationService;
        this.pageableSanitizer = pageableSanitizer;
        this.disputeMapper = disputeMapper;
    }

    @Override
    @Transactional
    public Dispute openDispute(UUID jobAssignmentId, String reason) {
        JobAssignment assignment = jobAssignmentService.getDetailedByIdForUpdate(jobAssignmentId);

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
        UUID assignmentId = disputeDao.findJobAssignmentIdById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.NOT_FOUND));
        jobAssignmentService.getDetailedByIdForUpdate(assignmentId);
        Dispute dispute = disputeDao.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.NOT_FOUND));

        if (resolution.movesMoney()) {
            StaffPermission permission = resolution.getMoneyPermission();
            boolean allowed = actor != null && actor.isRegistered() && actor.holds(permission);
            StaffAuditContext.reclassify(permission, resolution.getMoneyAuditAction(), allowed);
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
            dispute.setResolvedByStaff(null);
        } else {
            dispute.setResolvedBy(null);
            dispute.setResolvedByStaff(staffAccountService.getReference(actor.staffId()));
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

    @Override
    @Transactional
    public DisputeEvidence attachEvidence(UUID disputeId, MultipartFile file) {
        UUID actorId = securityService.getAuthenticatedUserId();
        Dispute dispute = disputeDao.findByIdForUpdate(disputeId)
                .orElseThrow(() -> Concealment.notFound(DisputeMessages.NOT_FOUND, actorId, disputeId,
                        "dispute is missing"));

        if (!isParticipantOf(dispute, actorId)) {
            throw Concealment.notFound(DisputeMessages.NOT_FOUND, actorId, disputeId, "not a party of the assignment");
        }

        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new ConflictException(DisputeMessages.EVIDENCE_DISPUTE_CLOSED);
        }

        if (disputeEvidenceDao.countByDisputeId(disputeId) >= uploadProperties.getMaxEvidencePerDispute()) {
            throw new ConflictException(DisputeMessages.EVIDENCE_LIMIT_REACHED);
        }

        uploadValidator.validate(file, FolderType.DISPUTE_EVIDENCE,
                DisputeMessages.EVIDENCE_EMPTY, DisputeMessages.EVIDENCE_TOO_LARGE);

        byte[] content = contentOf(file, actorId);
        String fileName = file.getOriginalFilename();
        String contentType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        String key = storageService.uploadFile(content, fileName, contentType, FolderType.DISPUTE_EVIDENCE);
        StorageRollbackCleanup.deleteOnRollback(storageService, key);

        DisputeEvidence evidence = new DisputeEvidence();
        evidence.setDispute(dispute);
        evidence.setUploadedBy(userService.getReference(actorId));
        evidence.setStorageKey(key);
        evidence.setOriginalFileName(fileName);
        evidence.setContentType(contentType);
        evidence.setFileSize(content.length);
        evidence.setChecksumSha256(Checksums.sha256Hex(content));

        DisputeEvidence saved = disputeEvidenceDao.save(evidence);
        log.info("Evidence {} attached to dispute {} by user {}", saved.getId(), disputeId, actorId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeEvidence> getEvidence(UUID disputeId) {
        readableDispute(disputeId);
        return disputeEvidenceDao.findAllByDisputeIdOrderByCreatedAtAsc(disputeId);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeEvidence getEvidenceForDownload(UUID disputeId, UUID evidenceId) {
        readableDispute(disputeId);
        return disputeEvidenceDao.findByIdAndDisputeId(evidenceId, disputeId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.EVIDENCE_NOT_FOUND));
    }

    private void readableDispute(UUID disputeId) {
        UUID actorId = securityService.getAuthenticatedUserId();
        Dispute dispute = disputeDao.findById(disputeId)
                .orElseThrow(() -> Concealment.notFound(DisputeMessages.NOT_FOUND, actorId, disputeId,
                        "dispute is missing"));

        if (!isParticipantOf(dispute, actorId)) {
            throw Concealment.notFound(DisputeMessages.NOT_FOUND, actorId, disputeId, "not a party of the assignment");
        }
    }

    private boolean isParticipantOf(Dispute dispute, UUID actorId) {
        return participants.isParticipant(
                jobAssignmentService.getDetailedById(dispute.getJobAssignment().getId()), actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffDisputeEvidenceDto> getEvidenceForStaff(UUID disputeId) {
        Dispute dispute = disputeDao.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.NOT_FOUND));
        JobAssignment assignment = jobAssignmentService.getDetailedById(dispute.getJobAssignment().getId());

        return disputeEvidenceDao.findAllByDisputeIdOrderByCreatedAtAsc(disputeId).stream()
                .map(evidence -> disputeMapper.toStaffEvidenceDto(evidence,
                        evidenceUploaderSide(assignment, evidence.getUploadedBy().getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeEvidence getEvidenceForStaffDownload(UUID disputeId, UUID evidenceId) {
        return disputeEvidenceDao.findByIdAndDisputeId(evidenceId, disputeId)
                .orElseThrow(() -> new ResourceNotFoundException(DisputeMessages.EVIDENCE_NOT_FOUND));
    }

    private EvidenceUploaderSide evidenceUploaderSide(JobAssignment assignment, UUID uploaderId) {
        return assignment.getWorker().getId().equals(uploaderId)
                ? EvidenceUploaderSide.WORKER
                : EvidenceUploaderSide.EMPLOYER;
    }

    private byte[] contentOf(MultipartFile file, UUID actorId) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("Dispute evidence could not be read for user {}", actorId, e);
            throw new ValidationException(DisputeMessages.EVIDENCE_UPLOAD_FAILED);
        }
    }
}
