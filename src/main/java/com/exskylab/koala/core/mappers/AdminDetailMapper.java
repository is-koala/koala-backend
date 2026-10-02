package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.common.LinkedRecordDto;
import com.exskylab.koala.core.dtos.job.response.AdminJobDetailDto;
import com.exskylab.koala.core.dtos.job.response.AdminJobDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminApplicationDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDetailDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminCancellationDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminTimelineEventDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminReviewDto;
import com.exskylab.koala.core.dtos.payment.response.AdminPaymentDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.dtos.payment.response.AdminRefundDto;
import com.exskylab.koala.core.dtos.payment.response.AdminSettlementTaskDto;
import com.exskylab.koala.core.dtos.payout.response.AdminPayoutDetailDto;
import com.exskylab.koala.core.dtos.user.response.AdminUserCompanyDto;
import com.exskylab.koala.core.dtos.user.response.AdminUserCountsDto;
import com.exskylab.koala.core.dtos.user.response.AdminSessionSummaryDto;
import com.exskylab.koala.core.dtos.user.response.AdminUserDetailDto;
import com.exskylab.koala.core.dtos.user.response.StaffPartyDto;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.PlainDecimalStringConverter;
import com.exskylab.koala.dataAccess.StaffAssignmentSummary;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.IdentityVerification;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.RefundOrigin;
import com.exskylab.koala.entities.Report;
import com.exskylab.koala.entities.Review;
import com.exskylab.koala.entities.SettlementTask;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.UserBadge;
import com.exskylab.koala.entities.WorkSession;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class, PricingDtoMapper.class, AddressMapper.class, CompanyMapper.class,
                GalleryMapper.class, JobCategoryMapper.class, TimesheetMapper.class, BadgeMapper.class,
                MediaUrlResolver.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        imports = {LogMasks.class, PlainDecimalStringConverter.class})
public interface AdminDetailMapper {

    StaffPartyDto toStaffPartyDto(User user);

    @Mapping(target = "id", source = "company.id")
    @Mapping(target = "name", source = "company.name")
    AdminUserCompanyDto toAdminUserCompanyDto(CompanyContact membership);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "profilePictureUrl", source = "user.profilePicture", qualifiedByName = "imageToPublicUrl")
    @Mapping(target = "email", expression = "java(includePii ? user.getEmail() : null)")
    @Mapping(target = "phoneNumber", expression = "java(includePii ? user.getPhoneNumber() : null)")
    @Mapping(target = "emailVerified", source = "user.emailVerified")
    @Mapping(target = "phoneVerified", source = "user.phoneVerified")
    @Mapping(target = "identityVerified", source = "user.identityVerified")
    @Mapping(target = "isEmployer", source = "user.employer")
    @Mapping(target = "createdAt", source = "user.createdAt")
    @Mapping(target = "companies", source = "memberships")
    @Mapping(target = "badges", source = "badges")
    @Mapping(target = "counts", source = "counts")
    @Mapping(target = "identityReviewId", source = "latestIdentityReview.id")
    @Mapping(target = "identityReviewStatus",
            expression = "java(latestIdentityReview == null ? null : latestIdentityReview.reviewStatus())")
    @Mapping(target = "pendingDeletionRequestId", source = "pendingDeletionRequestId")
    @Mapping(target = "sessionSummary", source = "sessionSummary")
    AdminUserDetailDto toAdminUserDetailDto(User user, boolean includePii, List<CompanyContact> memberships,
                                            List<UserBadge> badges, AdminUserCountsDto counts,
                                            IdentityVerification latestIdentityReview,
                                            UUID pendingDeletionRequestId, AdminSessionSummaryDto sessionSummary);

    LinkedRecordDto toLinkedRecordDto(Payment payment);

    LinkedRecordDto toLinkedRecordDto(Payout payout);

    LinkedRecordDto toLinkedRecordDto(Dispute dispute);

    LinkedRecordDto toLinkedRecordDto(Report report);

    @Mapping(target = "id", source = "refund.id")
    @Mapping(target = "paymentId", source = "refund.payment.id")
    @Mapping(target = "amount", expression = "java(refund.getAmount() == null ? null : refund.getAmount().toPlainString())")
    @Mapping(target = "currency", source = "refund.currency")
    @Mapping(target = "status", source = "refund.status")
    @Mapping(target = "reason", source = "refund.reason")
    @Mapping(target = "providerRefundId", source = "refund.providerRefundId")
    @Mapping(target = "origin", source = "origin")
    @Mapping(target = "originId", source = "originId")
    @Mapping(target = "completedAt", source = "refund.completedAt")
    @Mapping(target = "createdAt", source = "refund.createdAt")
    @Mapping(target = "updatedAt", source = "refund.updatedAt")
    AdminRefundDto toAdminRefundDto(Refund refund, RefundOrigin origin, UUID originId);

    @Mapping(target = "amount", expression = "java(task.getAmount() == null ? null : task.getAmount().toPlainString())")
    AdminSettlementTaskDto toAdminSettlementTaskDto(SettlementTask task);

    @Mapping(target = "id", source = "payment.id")
    @Mapping(target = "jobAssignmentId", source = "assignment.id")
    @Mapping(target = "jobId", source = "assignment.jobId")
    @Mapping(target = "jobTitle", source = "assignment.jobTitle")
    @Mapping(target = "companyId", source = "assignment.companyId")
    @Mapping(target = "companyName", source = "assignment.companyName")
    @Mapping(target = "worker", source = "worker")
    @Mapping(target = "employer", source = "employer")
    @Mapping(target = "status", source = "payment.status")
    @Mapping(target = "currency", source = "payment.currency")
    @Mapping(target = "pricingVersion", source = "payment.pricingVersion")
    @Mapping(target = "pricing", source = "payment")
    @Mapping(target = "workerPayout", expression = "java(payment.getWorkerPayout() == null ? null : payment.getWorkerPayout().toPlainString())")
    @Mapping(target = "commissionAmount", expression = "java(payment.getCommissionAmount() == null ? null : payment.getCommissionAmount().toPlainString())")
    @Mapping(target = "commissionRate", expression = "java(payment.getCommissionRate() == null ? null : payment.getCommissionRate().toPlainString())")
    @Mapping(target = "totalCharged", expression = "java(payment.getTotalCharged() == null ? null : payment.getTotalCharged().toPlainString())")
    @Mapping(target = "conversationId", source = "payment.conversationId")
    @Mapping(target = "providerPaymentId", source = "payment.providerPaymentId")
    @Mapping(target = "providerTransactionId", source = "payment.providerTransactionId")
    @Mapping(target = "failureReason", source = "payment.failureReason")
    @Mapping(target = "verificationAttempts", source = "payment.verificationAttempts")
    @Mapping(target = "nextVerificationAt", source = "payment.nextVerificationAt")
    @Mapping(target = "completedAt", source = "payment.completedAt")
    @Mapping(target = "createdAt", source = "payment.createdAt")
    @Mapping(target = "updatedAt", source = "payment.updatedAt")
    @Mapping(target = "payoutId", source = "payoutId")
    @Mapping(target = "refunds", source = "refunds")
    AdminPaymentDto toAdminPaymentDto(Payment payment, StaffAssignmentSummary assignment, User worker, User employer,
                                      UUID payoutId, List<AdminRefundDto> refunds);

    @Mapping(target = "id", source = "payout.id")
    @Mapping(target = "jobAssignmentId", source = "payout.jobAssignment.id")
    @Mapping(target = "jobId", source = "assignment.job.id")
    @Mapping(target = "jobTitle", source = "assignment.job.title")
    @Mapping(target = "companyId", source = "assignment.job.company.id")
    @Mapping(target = "companyName", source = "assignment.job.company.name")
    @Mapping(target = "worker", source = "worker")
    @Mapping(target = "employer", source = "assignment.job.creator")
    @Mapping(target = "paymentId", source = "payout.payment.id")
    @Mapping(target = "bankAccountId", source = "payout.bankAccount.id")
    @Mapping(target = "releaseOrigin", source = "payout.releaseOrigin")
    @Mapping(target = "settledAt", source = "payout.completedAt")
    @Mapping(target = "reconciledByStaffId", source = "payout.reconciledByStaffId")
    @Mapping(target = "maskedIban",
            expression = "java(payout.getBankAccount() == null ? null : LogMasks.iban(payout.getBankAccount().getIban()))")
    @Mapping(target = "amount", expression = "java(payout.getAmount() == null ? null : payout.getAmount().toPlainString())")
    @Mapping(target = "currency", source = "payout.currency")
    @Mapping(target = "status", source = "payout.status")
    @Mapping(target = "providerReference", source = "payout.providerReference")
    @Mapping(target = "settlementReference", source = "payout.settlementReference")
    @Mapping(target = "failureReason", source = "payout.failureReason")
    @Mapping(target = "processingAt", source = "payout.processingAt")
    @Mapping(target = "completedAt", source = "payout.completedAt")
    @Mapping(target = "createdAt", source = "payout.createdAt")
    @Mapping(target = "updatedAt", source = "payout.updatedAt")
    @Mapping(target = "settlementTasks", source = "settlementTasks")
    @Mapping(target = "paymentAttempts", source = "paymentAttempts")
    AdminPayoutDetailDto toAdminPayoutDetailDto(Payout payout, JobAssignment assignment, User worker,
                                                List<SettlementTask> settlementTasks,
                                                List<Payment> paymentAttempts);

    AdminApplicationDto toAdminApplicationDto(JobApplication application);

    @Mapping(target = "authorId", source = "author.id")
    @Mapping(target = "subjectId", source = "subject.id")
    AdminReviewDto toAdminReviewDto(Review review);

    @Mapping(target = "feeRate", expression = "java(PlainDecimalStringConverter.convert(cancellation.getFeeRate()))")
    @Mapping(target = "feeAmount", expression = "java(PlainDecimalStringConverter.convert(cancellation.getFeeAmount()))")
    @Mapping(target = "refundAmount", expression = "java(PlainDecimalStringConverter.convert(cancellation.getRefundAmount()))")
    @Mapping(target = "refundId", source = "refund.id")
    AdminCancellationDto toAdminCancellationDto(Cancellation cancellation);

    @Mapping(target = "id", source = "assignment.id")
    @Mapping(target = "jobId", source = "assignment.job.id")
    @Mapping(target = "jobTitle", source = "assignment.job.title")
    @Mapping(target = "companyId", source = "assignment.job.company.id")
    @Mapping(target = "companyName", source = "assignment.job.company.name")
    @Mapping(target = "employerType", source = "assignment.job.employerType")
    @Mapping(target = "worker", source = "assignment.worker")
    @Mapping(target = "employer", source = "assignment.job.creator")
    @Mapping(target = "status", source = "assignment.status")
    @Mapping(target = "paymentStatus", source = "assignment.paymentStatus")
    @Mapping(target = "assignmentDateTime", source = "assignment.assignmentDateTime")
    @Mapping(target = "expectedCompletionDateTime", source = "assignment.expectedCompletionDateTime")
    @Mapping(target = "actualCompletionDateTime", source = "assignment.actualCompletionDateTime")
    @Mapping(target = "workHours", source = "assignment.workHours")
    @Mapping(target = "notes", source = "assignment.notes")
    @Mapping(target = "pricing", source = "assignment")
    @Mapping(target = "application", source = "assignment.application")
    @Mapping(target = "workSessions", source = "workSessions")
    @Mapping(target = "cancellation", source = "cancellation")
    @Mapping(target = "reviews", source = "reviews")
    @Mapping(target = "payments", source = "payments")
    @Mapping(target = "payout", source = "payout")
    @Mapping(target = "disputes", source = "disputes")
    @Mapping(target = "reports", source = "reports")
    @Mapping(target = "transport", source = "transport")
    @Mapping(target = "timeline", source = "timeline")
    @Mapping(target = "createdAt", source = "assignment.createdAt")
    @Mapping(target = "updatedAt", source = "assignment.updatedAt")
    AdminAssignmentDetailDto toAdminAssignmentDetailDto(JobAssignment assignment, List<WorkSession> workSessions,
                                                        Cancellation cancellation, List<Review> reviews,
                                                        List<Payment> payments, Payout payout,
                                                        List<Dispute> disputes, List<Report> reports,
                                                        AssignmentTransportDto transport,
                                                        List<AdminTimelineEventDto> timeline);

    @Mapping(target = "id", source = "job.id")
    @Mapping(target = "title", source = "job.title")
    @Mapping(target = "status", source = "job.status")
    @Mapping(target = "employerType", source = "job.employerType")
    @Mapping(target = "type", source = "job.type")
    @Mapping(target = "salaryType", source = "job.salaryType")
    @Mapping(target = "salary", source = "job.salary")
    @Mapping(target = "pricing", source = "job")
    @Mapping(target = "startDateTime", source = "job.startDateTime")
    @Mapping(target = "endDateTime", source = "job.endDateTime")
    @Mapping(target = "employer", source = "job.creator")
    @Mapping(target = "companyId", source = "job.company.id")
    @Mapping(target = "companyName", source = "job.company.name")
    @Mapping(target = "cityName", source = "job.address.city.name")
    @Mapping(target = "districtName", source = "job.address.district.name")
    @Mapping(target = "headcount", source = "job.headcount")
    @Mapping(target = "applicationCount", source = "applicationCount")
    @Mapping(target = "assignmentCount", source = "assignmentCount")
    @Mapping(target = "createdAt", source = "job.createdAt")
    @Mapping(target = "updatedAt", source = "job.updatedAt")
    AdminJobDto toAdminJobDto(Job job, Long applicationCount, Long assignmentCount);

    @Mapping(target = "id", source = "job.id")
    @Mapping(target = "title", source = "job.title")
    @Mapping(target = "description", source = "job.description")
    @Mapping(target = "duties", source = "job.duties")
    @Mapping(target = "sector", source = "job.sector")
    @Mapping(target = "position", source = "job.position")
    @Mapping(target = "dressCode", source = "job.dressCode")
    @Mapping(target = "category", source = "job.category")
    @Mapping(target = "status", source = "job.status")
    @Mapping(target = "employerType", source = "job.employerType")
    @Mapping(target = "type", source = "job.type")
    @Mapping(target = "salaryType", source = "job.salaryType")
    @Mapping(target = "salary", source = "job.salary")
    @Mapping(target = "pricing", source = "job")
    @Mapping(target = "minAge", source = "job.minAge")
    @Mapping(target = "maxAge", source = "job.maxAge")
    @Mapping(target = "insuranceRequired", source = "job.insuranceRequired")
    @Mapping(target = "experienceRequired", source = "job.experienceRequired")
    @Mapping(target = "headcount", source = "job.headcount")
    @Mapping(target = "startDateTime", source = "job.startDateTime")
    @Mapping(target = "endDateTime", source = "job.endDateTime")
    @Mapping(target = "employer", source = "job.creator")
    @Mapping(target = "creator", source = "job.creator")
    @Mapping(target = "company", source = "job.company")
    @Mapping(target = "address", source = "job.address")
    @Mapping(target = "addressPublic", source = "job.addressPublic")
    @Mapping(target = "images", source = "job.images")
    @Mapping(target = "applicationCount", source = "applicationCount")
    @Mapping(target = "assignmentCount", source = "assignmentCount")
    @Mapping(target = "cancelledAt", source = "job.cancelledAt")
    @Mapping(target = "cancelReason", source = "job.cancelReason")
    @Mapping(target = "createdAt", source = "job.createdAt")
    @Mapping(target = "updatedAt", source = "job.updatedAt")
    AdminJobDetailDto toAdminJobDetailDto(Job job, Long applicationCount, Long assignmentCount);
}
