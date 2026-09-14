package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AdminDetailService;
import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.business.abstracts.ReviewService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDetailDto;
import com.exskylab.koala.core.dtos.payment.response.AdminPaymentDto;
import com.exskylab.koala.core.mappers.AdminDetailMapper;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.Refund;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminDetailManager implements AdminDetailService {

    private final JobAssignmentService jobAssignmentService;
    private final PaymentService paymentService;
    private final PayoutService payoutService;
    private final DisputeService disputeService;
    private final ReportService reportService;
    private final TimesheetService timesheetService;
    private final CancellationService cancellationService;
    private final ReviewService reviewService;
    private final AdminDetailMapper adminDetailMapper;

    public AdminDetailManager(JobAssignmentService jobAssignmentService, PaymentService paymentService,
                              PayoutService payoutService, DisputeService disputeService,
                              ReportService reportService, TimesheetService timesheetService,
                              CancellationService cancellationService, ReviewService reviewService,
                              AdminDetailMapper adminDetailMapper) {
        this.jobAssignmentService = jobAssignmentService;
        this.paymentService = paymentService;
        this.payoutService = payoutService;
        this.disputeService = disputeService;
        this.reportService = reportService;
        this.timesheetService = timesheetService;
        this.cancellationService = cancellationService;
        this.reviewService = reviewService;
        this.adminDetailMapper = adminDetailMapper;
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
        List<Payment> attempts = paymentService.getAttemptsForStaff(assignmentId);
        Map<UUID, List<Refund>> refunds = refundsByPayment(attempts);

        return attempts.stream()
                .map(payment -> adminDetailMapper.toAdminPaymentDto(payment, assignment,
                        refunds.getOrDefault(payment.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentDto getPayment(UUID paymentId) {
        Payment payment = paymentService.getForStaff(paymentId);
        List<Refund> refunds = refundsByPayment(List.of(payment)).getOrDefault(paymentId, List.of());

        return adminDetailMapper.toAdminPaymentDto(payment, payment.getJobAssignment(), refunds);
    }

    private Map<UUID, List<Refund>> refundsByPayment(List<Payment> payments) {
        Set<UUID> ids = payments.stream().map(Payment::getId).collect(Collectors.toSet());
        return payoutService.getRefundsForPayments(ids).stream()
                .collect(Collectors.groupingBy(refund -> refund.getPayment().getId()));
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
