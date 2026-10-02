package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuditSubjectService;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.helpers.AuditSubjectSource;
import com.exskylab.koala.core.security.AuditSubject;
import com.exskylab.koala.core.security.AuditSubjects;
import com.exskylab.koala.dataAccess.AssignmentParties;
import com.exskylab.koala.dataAccess.ReportedParty;
import com.exskylab.koala.entities.AuditSubjectType;
import com.exskylab.koala.entities.StaffResourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class AuditSubjectManager implements AuditSubjectService {

    private final UserService userService;
    private final CompanyService companyService;
    private final IdentityDocumentReviewService identityDocumentReviewService;
    private final LegalService legalService;
    private final GalleryService galleryService;
    private final BankAccountService bankAccountService;
    private final JobAssignmentService jobAssignmentService;
    private final JobService jobService;
    private final PayoutService payoutService;
    private final PaymentService paymentService;
    private final DisputeService disputeService;
    private final ReportService reportService;

    public AuditSubjectManager(UserService userService, CompanyService companyService,
                               IdentityDocumentReviewService identityDocumentReviewService,
                               LegalService legalService, GalleryService galleryService,
                               BankAccountService bankAccountService, JobAssignmentService jobAssignmentService,
                               JobService jobService, PayoutService payoutService, PaymentService paymentService,
                               DisputeService disputeService, ReportService reportService) {
        this.userService = userService;
        this.companyService = companyService;
        this.identityDocumentReviewService = identityDocumentReviewService;
        this.legalService = legalService;
        this.galleryService = galleryService;
        this.bankAccountService = bankAccountService;
        this.jobAssignmentService = jobAssignmentService;
        this.jobService = jobService;
        this.payoutService = payoutService;
        this.paymentService = paymentService;
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

    @Override
    public AuditSubjects forStaffTarget(StaffResourceType resourceType, UUID resourceId) {
        if (resourceType == null || resourceId == null) {
            return AuditSubjects.NONE;
        }
        return switch (resourceType) {
            case USER -> AuditSubjects.of(existing(AuditSubject.user(resourceId)));
            case COMPANY -> AuditSubjects.of(existing(AuditSubject.company(resourceId)));
            case JOB_ASSIGNMENT -> partiesOf(resourceId);
            case DISPUTE -> partiesOfDispute(resourceId);
            case REPORT -> reportedPartyOf(resourceId);
            case JOB -> employerOfJob(resourceId);
        };
    }

    @Override
    public boolean exists(AuditSubject subject) {
        if (subject == null) {
            return false;
        }
        return subject.type() == AuditSubjectType.USER
                ? userService.existsIncludingDeleted(subject.id())
                : companyService.existsIncludingDeleted(subject.id());
    }

    private AuditSubjects resolve(AuditSubjectSource source, String resourceId) {
        return switch (source) {
            case USER -> byId(resourceId, id -> AuditSubjects.of(existing(AuditSubject.user(id))));
            case COMPANY -> byId(resourceId, id -> AuditSubjects.of(existing(AuditSubject.company(id))));
            case IDENTITY_REVIEW -> byId(resourceId, id -> AuditSubjects.of(
                    identityDocumentReviewService.findReviewedUserId(id).map(AuditSubject::user).orElse(null)));
            case DELETION_REQUEST -> byId(resourceId, id -> AuditSubjects.of(
                    legalService.findDeletionRequesterId(id).map(AuditSubject::user).orElse(null)));
            case IMAGE -> byId(resourceId, id -> AuditSubjects.of(galleryService.findImageOwner(id).orElse(null)));
            case BANK_ACCOUNT_VERIFICATION -> AuditSubjects.of(
                    bankAccountService.findOwnerIdByVerificationReference(resourceId)
                            .map(AuditSubject::user)
                            .orElse(null));
            case JOB -> byId(resourceId, this::employerOfJob);
            case ASSIGNMENT -> byId(resourceId, this::partiesOf);
            case PAYOUT -> byId(resourceId, id -> AuditSubjects.of(
                    payoutService.findWorkerIdOf(id).map(AuditSubject::user).orElse(null)));
            case PAYMENT -> byId(resourceId, id -> paymentService.findJobAssignmentIdOf(id)
                    .map(this::partiesOf)
                    .orElse(AuditSubjects.NONE));
            case DISPUTE -> byId(resourceId, this::partiesOfDispute);
            case REPORT -> byId(resourceId, this::reportedPartyOf);
            case STAFF_NOTES, STAFF_FLAGS, BADGE_CATALOGUE, CATEGORY_CATALOGUE, RUNTIME_SETTINGS, STAFF_ACCOUNTS,
                 STAFF_ROLES, CLIENT_EVENTS -> AuditSubjects.NONE;
        };
    }

    private AuditSubject existing(AuditSubject subject) {
        return exists(subject) ? subject : null;
    }

    private AuditSubjects employerOfJob(UUID jobId) {
        return AuditSubjects.of(jobService.findEmployerIds(jobId)
                .map(ids -> AuditSubject.employerOf(ids.getCompanyId(), ids.getCreatorId()))
                .orElse(null));
    }

    private AuditSubjects partiesOf(UUID jobAssignmentId) {
        Optional<AssignmentParties> parties = jobAssignmentService.findAuditParties(jobAssignmentId);
        return parties
                .map(found -> AuditSubjects.of(
                        found.getWorkerId() == null ? null : AuditSubject.user(found.getWorkerId()),
                        AuditSubject.employerOf(found.getCompanyId(), found.getCreatorId())))
                .orElse(AuditSubjects.NONE);
    }

    private AuditSubjects partiesOfDispute(UUID disputeId) {
        return disputeService.findJobAssignmentIdOf(disputeId)
                .map(this::partiesOf)
                .orElse(AuditSubjects.NONE);
    }

    private AuditSubjects reportedPartyOf(UUID reportId) {
        return reportService.findReportedParty(reportId)
                .map(this::reportedPartyOf)
                .orElse(AuditSubjects.NONE);
    }

    private AuditSubjects reportedPartyOf(ReportedParty party) {
        if (party.getReportedUserId() != null) {
            return AuditSubjects.of(AuditSubject.user(party.getReportedUserId()));
        }
        AuditSubject employer = AuditSubject.employerOf(party.getJobCompanyId(), party.getJobCreatorId());
        if (employer != null) {
            return AuditSubjects.of(employer);
        }
        return party.getImageId() == null
                ? AuditSubjects.NONE
                : AuditSubjects.of(galleryService.findImageOwner(party.getImageId()).orElse(null));
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
