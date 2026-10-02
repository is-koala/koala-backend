package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuditSubjectService;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.core.helpers.AuditSubjectSource;
import com.exskylab.koala.core.security.AuditSubject;
import com.exskylab.koala.core.security.AuditSubjects;
import com.exskylab.koala.dataAccess.StaffAssignmentSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class AuditSubjectManager implements AuditSubjectService {

    private final IdentityDocumentReviewService identityDocumentReviewService;
    private final LegalService legalService;
    private final GalleryService galleryService;
    private final BankAccountService bankAccountService;
    private final JobAssignmentService jobAssignmentService;
    private final PayoutService payoutService;
    private final DisputeService disputeService;
    private final ReportService reportService;

    public AuditSubjectManager(IdentityDocumentReviewService identityDocumentReviewService,
                               LegalService legalService, GalleryService galleryService,
                               BankAccountService bankAccountService, JobAssignmentService jobAssignmentService,
                               PayoutService payoutService, DisputeService disputeService,
                               ReportService reportService) {
        this.identityDocumentReviewService = identityDocumentReviewService;
        this.legalService = legalService;
        this.galleryService = galleryService;
        this.bankAccountService = bankAccountService;
        this.jobAssignmentService = jobAssignmentService;
        this.payoutService = payoutService;
        this.disputeService = disputeService;
        this.reportService = reportService;
    }

    @Override
    public AuditSubjects resolve(String resourceType, String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return AuditSubjects.NONE;
        }
        return AuditSubjectSource.of(resourceType)
                .map(source -> resolve(source, resourceId))
                .orElse(AuditSubjects.NONE);
    }

    private AuditSubjects resolve(AuditSubjectSource source, String resourceId) {
        return switch (source) {
            case USER -> byId(resourceId, id -> AuditSubjects.of(AuditSubject.user(id)));
            case COMPANY -> byId(resourceId, id -> AuditSubjects.of(AuditSubject.company(id)));
            case IDENTITY_REVIEW -> byId(resourceId, id -> AuditSubjects.of(
                    identityDocumentReviewService.findReviewedUserId(id).map(AuditSubject::user).orElse(null)));
            case DELETION_REQUEST -> byId(resourceId, id -> AuditSubjects.of(
                    legalService.findDeletionRequesterId(id).map(AuditSubject::user).orElse(null)));
            case IMAGE -> byId(resourceId, id -> AuditSubjects.of(galleryService.findImageOwner(id).orElse(null)));
            case BANK_ACCOUNT_VERIFICATION -> AuditSubjects.of(
                    bankAccountService.findOwnerIdByVerificationReference(resourceId)
                            .map(AuditSubject::user)
                            .orElse(null));
            case ASSIGNMENT -> byId(resourceId, this::partiesOf);
            case PAYOUT -> byId(resourceId, id -> AuditSubjects.of(
                    payoutService.findWorkerIdOf(id).map(AuditSubject::user).orElse(null)));
            case DISPUTE -> byId(resourceId, id -> disputeService.findJobAssignmentIdOf(id)
                    .map(this::partiesOf)
                    .orElse(AuditSubjects.NONE));
            case REPORT -> byId(resourceId, id -> AuditSubjects.of(reportService.findReportedParty(id).orElse(null)));
            case BADGE_CATALOGUE, CATEGORY_CATALOGUE, RUNTIME_SETTINGS, STAFF_ACCOUNTS, STAFF_ROLES,
                 STAFF_NOTES, STAFF_FLAGS -> AuditSubjects.NONE;
        };
    }

    private AuditSubjects partiesOf(UUID jobAssignmentId) {
        List<StaffAssignmentSummary> summaries = jobAssignmentService.findStaffSummaries(List.of(jobAssignmentId));
        if (summaries.isEmpty()) {
            return AuditSubjects.NONE;
        }
        StaffAssignmentSummary summary = summaries.getFirst();
        return AuditSubjects.of(
                summary.getWorkerId() == null ? null : AuditSubject.user(summary.getWorkerId()),
                AuditSubject.employerOf(summary.getCompanyId(), summary.getEmployerId()));
    }

    private static AuditSubjects byId(String resourceId, Function<UUID, AuditSubjects> lookup) {
        UUID id;
        try {
            id = UUID.fromString(resourceId);
        } catch (IllegalArgumentException malformed) {
            log.debug("Audit resource id is not a UUID, no subject resolved: {}", resourceId);
            return AuditSubjects.NONE;
        }
        return lookup.apply(id);
    }
}
