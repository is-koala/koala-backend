package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AdminDetailService;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.business.abstracts.ReviewService;
import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.bankAccount.response.AdminBankAccountDto;
import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.core.dtos.identity.response.IdentityReviewDto;
import com.exskylab.koala.core.dtos.legal.response.AccountDeletionDto;
import com.exskylab.koala.core.dtos.legal.response.DeletionBlockerDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDetailDto;
import com.exskylab.koala.core.dtos.payment.response.AdminPaymentDto;
import com.exskylab.koala.core.dtos.payment.response.AdminRefundDto;
import com.exskylab.koala.core.dtos.user.response.AdminUserCountsDto;
import com.exskylab.koala.core.dtos.user.response.AdminUserDetailDto;
import com.exskylab.koala.core.mappers.AdminDetailMapper;
import com.exskylab.koala.core.mappers.BankAccountMapper;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.core.mappers.LegalMapper;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.security.StaffAuditContext;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.dataAccess.StaffAssignmentSummary;
import com.exskylab.koala.entities.AccountDeletionRequest;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.IdentityVerification;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeResolution;
import com.exskylab.koala.entities.DisputeStatus;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.RefundOrigin;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class AdminDetailManager implements AdminDetailService {

    private final JobAssignmentService jobAssignmentService;
    private final PaymentService paymentService;
    private final PayoutService payoutService;
    private final DisputeService disputeService;
    private final ReportService reportService;
    private final TimesheetService timesheetService;
    private final CancellationService cancellationService;
    private final ReviewService reviewService;
    private final UserService userService;
    private final JobApplicationService jobApplicationService;
    private final CompanyContactService companyContactService;
    private final BadgeService badgeService;
    private final IdentityDocumentReviewService identityDocumentReviewService;
    private final LegalService legalService;
    private final CompanyService companyService;
    private final JobService jobService;
    private final StaffAccountService staffAccountService;
    private final BankAccountService bankAccountService;
    private final SessionService sessionService;
    private final AdminDetailMapper adminDetailMapper;
    private final CompanyMapper companyMapper;
    private final LegalMapper legalMapper;
    private final BankAccountMapper bankAccountMapper;
    private final MessageResolver messageResolver;

    public AdminDetailManager(JobAssignmentService jobAssignmentService, PaymentService paymentService,
                              PayoutService payoutService, DisputeService disputeService,
                              ReportService reportService, TimesheetService timesheetService,
                              CancellationService cancellationService, ReviewService reviewService,
                              UserService userService, JobApplicationService jobApplicationService,
                              CompanyContactService companyContactService, BadgeService badgeService,
                              IdentityDocumentReviewService identityDocumentReviewService,
                              LegalService legalService, CompanyService companyService, JobService jobService,
                              StaffAccountService staffAccountService, BankAccountService bankAccountService,
                              SessionService sessionService, AdminDetailMapper adminDetailMapper,
                              CompanyMapper companyMapper, LegalMapper legalMapper,
                              BankAccountMapper bankAccountMapper, MessageResolver messageResolver) {
        this.jobAssignmentService = jobAssignmentService;
        this.paymentService = paymentService;
        this.payoutService = payoutService;
        this.disputeService = disputeService;
        this.reportService = reportService;
        this.timesheetService = timesheetService;
        this.cancellationService = cancellationService;
        this.reviewService = reviewService;
        this.userService = userService;
        this.jobApplicationService = jobApplicationService;
        this.companyContactService = companyContactService;
        this.badgeService = badgeService;
        this.identityDocumentReviewService = identityDocumentReviewService;
        this.legalService = legalService;
        this.companyService = companyService;
        this.jobService = jobService;
        this.staffAccountService = staffAccountService;
        this.bankAccountService = bankAccountService;
        this.sessionService = sessionService;
        this.adminDetailMapper = adminDetailMapper;
        this.companyMapper = companyMapper;
        this.legalMapper = legalMapper;
        this.bankAccountMapper = bankAccountMapper;
        this.messageResolver = messageResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdentityReviewDto> identityReviews(List<IdentityVerification> reviews) {
        Set<UUID> reviewerIds = reviews.stream()
                .map(IdentityVerification::getReviewedByStaffId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = staffAccountService.displayNamesOf(reviewerIds);
        return reviews.stream().map(review -> IdentityReviewDto.from(review, names)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IdentityReviewDto identityReview(IdentityVerification review) {
        return identityReviews(List.of(review)).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDeletionDto> deletionRequests(List<AccountDeletionRequest> requests) {
        Map<UUID, List<DeletionBlockerDto>> blockers = legalService.findDeletionBlockers(requests);
        return requests.stream()
                .map(request -> legalMapper.toStaffAccountDeletionDto(request,
                        blockers.getOrDefault(request.getId(), List.of()), messageResolver))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDeletionDto deletionRequest(AccountDeletionRequest request) {
        return deletionRequests(List.of(request)).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CompanyReviewDto> searchCompanies(CompanyApprovalStatus status, String query, Pageable pageable) {
        Page<Company> companies = companyService.searchCompanies(status, query, pageable);
        List<UUID> ids = companies.getContent().stream().map(Company::getId).toList();
        Map<UUID, List<CompanyContact>> contacts = companyContactService.getContactsOfCompanies(ids).stream()
                .collect(Collectors.groupingBy(contact -> contact.getCompany().getId()));
        Map<UUID, Long> jobCounts = jobService.countForCompanies(ids);

        return companies.map(company -> companyMapper.toCompanyReviewDto(company,
                contacts.getOrDefault(company.getId(), List.of()),
                jobCounts.getOrDefault(company.getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyReviewDto getCompany(UUID companyId) {
        Company company = companyService.getCompanyById(companyId);
        return companyMapper.toCompanyReviewDto(company,
                companyContactService.getContactsOfCompany(companyId),
                jobService.countForCompany(companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailDto getUserDetail(StaffPrincipal actor, UUID userId) {
        User user = userService.getById(userId);
        boolean includePii = actor != null && actor.isRegistered() && actor.holds(StaffPermission.USER_READ_PII);
        if (includePii) {
            StaffAuditContext.reclassify(StaffPermission.USER_READ_PII, StaffPermission.USER_READ_PII.getCode(), true);
            log.info("Staff {} reads user {} with contact details", actor.staffId(), userId);
        }

        AdminUserCountsDto counts = new AdminUserCountsDto(
                jobApplicationService.countForApplicant(userId),
                jobAssignmentService.countAsWorker(userId),
                jobAssignmentService.countAsEmployer(userId),
                paymentService.countForEmployer(userId),
                payoutService.countForWorker(userId),
                disputeService.countInvolving(userId),
                reportService.countReceivedBy(userId),
                reportService.countMadeBy(userId));

        return adminDetailMapper.toAdminUserDetailDto(user, includePii,
                companyContactService.getMembershipsOf(userId),
                badgeService.getBadgesOf(userId),
                counts,
                identityDocumentReviewService.findLatestFor(userId).orElse(null),
                legalService.findOpenDeletionRequestId(userId).orElse(null),
                sessionService.summarizeForStaff(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminBankAccountDto> getBankAccountsOfUser(UUID userId) {
        userService.getById(userId);
        return bankAccountMapper.toAdminBankAccountDtoList(bankAccountService.getAccountsForStaff(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPaymentDto> searchPayments(List<PaymentAttemptStatus> statuses, Pageable pageable) {
        Page<Payment> page = paymentService.searchForStaff(statuses, pageable);
        Set<UUID> assignmentIds = page.getContent().stream()
                .map(payment -> payment.getJobAssignment().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, StaffAssignmentSummary> assignments = jobAssignmentService.findStaffSummaries(assignmentIds).stream()
                .collect(Collectors.toMap(StaffAssignmentSummary::getId, Function.identity()));
        Set<UUID> personIds = assignments.values().stream()
                .flatMap(summary -> Stream.of(summary.getWorkerId(), summary.getEmployerId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> people = userService.getAllByIdIn(personIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return new PageImpl<>(paymentDtos(page.getContent(), assignments, people),
                page.getPageable(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAssignmentDetailDto getAssignmentDetail(UUID assignmentId) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(assignmentId);

        return adminDetailMapper.toAdminAssignmentDetailDto(assignment,
                timesheetService.getSessionsForStaff(assignmentId),
                cancellationService.findForStaff(assignmentId).orElse(null),
                reviewService.getReviewsForStaff(assignmentId),
                paymentService.getAttemptsForStaff(assignmentId),
                payoutService.findForAssignment(assignmentId).orElse(null),
                disputeService.findAllForAssignment(assignmentId),
                reportService.findAllBetweenAssignmentParties(assignment.getJob().getId(), partiesOf(assignment)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminPaymentDto> getPaymentsForAssignment(UUID assignmentId) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(assignmentId);
        return paymentDtos(paymentService.getAttemptsForStaff(assignmentId),
                Map.of(assignment.getId(), LoadedAssignmentSummary.of(assignment)), peopleOf(assignment));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentDto getPayment(UUID paymentId) {
        Payment payment = paymentService.getForStaff(paymentId);
        JobAssignment assignment = payment.getJobAssignment();
        return paymentDtos(List.of(payment),
                Map.of(assignment.getId(), LoadedAssignmentSummary.of(assignment)), peopleOf(assignment)).getFirst();
    }

    private List<AdminPaymentDto> paymentDtos(List<Payment> attempts, Map<UUID, StaffAssignmentSummary> assignments,
                                              Map<UUID, User> people) {
        Set<UUID> paymentIds = attempts.stream().map(Payment::getId).collect(Collectors.toSet());
        Map<UUID, UUID> assignmentByPayment = attempts.stream()
                .collect(Collectors.toMap(Payment::getId, payment -> payment.getJobAssignment().getId()));
        Set<UUID> assignmentIds = new LinkedHashSet<>(assignmentByPayment.values());

        List<Refund> refunds = payoutService.getRefundsForPayments(paymentIds);
        Map<UUID, UUID> cancellationByRefund = cancellationService
                .findAllByRefundIds(refunds.stream().map(Refund::getId).toList()).stream()
                .collect(Collectors.toMap(cancellation -> cancellation.getRefund().getId(), Cancellation::getId));
        Map<UUID, UUID> refundingDisputeByAssignment = disputeService.findAllForAssignments(assignmentIds).stream()
                .filter(dispute -> dispute.getStatus() == DisputeStatus.RESOLVED
                        && dispute.getResolution() == DisputeResolution.REFUND_EMPLOYER)
                .collect(Collectors.toMap(dispute -> dispute.getJobAssignment().getId(), Dispute::getId,
                        (first, second) -> first));
        Map<UUID, Payout> payoutByAssignment = payoutService.findForAssignments(assignmentIds).stream()
                .collect(Collectors.toMap(payout -> payout.getJobAssignment().getId(), Function.identity(),
                        (first, second) -> first));

        Map<UUID, List<AdminRefundDto>> refundsByPayment = refunds.stream()
                .collect(Collectors.groupingBy(refund -> refund.getPayment().getId(),
                        Collectors.mapping(refund -> refundDto(refund, cancellationByRefund,
                                        refundingDisputeByAssignment.get(
                                                assignmentByPayment.get(refund.getPayment().getId()))),
                                Collectors.toList())));

        return attempts.stream()
                .map(payment -> {
                    UUID assignmentId = assignmentByPayment.get(payment.getId());
                    StaffAssignmentSummary assignment = assignments.get(assignmentId);
                    Payout payout = payoutByAssignment.get(assignmentId);
                    UUID paidOutPaymentId = payout == null || payout.getPayment() == null
                            ? null : payout.getPayment().getId();
                    return adminDetailMapper.toAdminPaymentDto(payment, assignment,
                            assignment == null ? null : people.get(assignment.getWorkerId()),
                            assignment == null ? null : people.get(assignment.getEmployerId()),
                            payment.getId().equals(paidOutPaymentId) ? payout.getId() : null,
                            refundsByPayment.getOrDefault(payment.getId(), List.of()));
                })
                .toList();
    }

    private static Map<UUID, User> peopleOf(JobAssignment assignment) {
        Map<UUID, User> people = new HashMap<>();
        if (assignment.getWorker() != null) {
            people.put(assignment.getWorker().getId(), assignment.getWorker());
        }
        if (assignment.getJob() != null && assignment.getJob().getCreator() != null) {
            people.put(assignment.getJob().getCreator().getId(), assignment.getJob().getCreator());
        }
        return people;
    }

    private record LoadedAssignmentSummary(UUID id, UUID workerId, UUID jobId, String jobTitle, UUID employerId,
                                           UUID companyId, String companyName) implements StaffAssignmentSummary {

        static LoadedAssignmentSummary of(JobAssignment assignment) {
            Job job = assignment.getJob();
            return new LoadedAssignmentSummary(assignment.getId(),
                    assignment.getWorker() == null ? null : assignment.getWorker().getId(),
                    job == null ? null : job.getId(),
                    job == null ? null : job.getTitle(),
                    job == null || job.getCreator() == null ? null : job.getCreator().getId(),
                    job == null || job.getCompany() == null ? null : job.getCompany().getId(),
                    job == null || job.getCompany() == null ? null : job.getCompany().getName());
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public UUID getWorkerId() {
            return workerId;
        }

        @Override
        public UUID getJobId() {
            return jobId;
        }

        @Override
        public String getJobTitle() {
            return jobTitle;
        }

        @Override
        public UUID getEmployerId() {
            return employerId;
        }

        @Override
        public UUID getCompanyId() {
            return companyId;
        }

        @Override
        public String getCompanyName() {
            return companyName;
        }
    }

    private AdminRefundDto refundDto(Refund refund, Map<UUID, UUID> cancellationByRefund, UUID refundingDispute) {
        UUID cancellationId = cancellationByRefund.get(refund.getId());
        if (cancellationId != null) {
            return adminDetailMapper.toAdminRefundDto(refund, RefundOrigin.CANCELLATION, cancellationId);
        }
        if (refundingDispute != null) {
            return adminDetailMapper.toAdminRefundDto(refund, RefundOrigin.DISPUTE, refundingDispute);
        }
        return adminDetailMapper.toAdminRefundDto(refund, RefundOrigin.STAFF, null);
    }

    private static Set<UUID> partiesOf(JobAssignment assignment) {
        Set<UUID> parties = new LinkedHashSet<>();
        if (assignment.getWorker() != null) {
            parties.add(assignment.getWorker().getId());
        }
        if (assignment.getJob().getCreator() != null) {
            parties.add(assignment.getJob().getCreator().getId());
        }
        return parties;
    }
}
