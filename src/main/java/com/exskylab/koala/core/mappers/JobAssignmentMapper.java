package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobApplication.response.AssignmentApplicationDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.PaymentSessionDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyReviewDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.ShortJobAssignmentDto;
import com.exskylab.koala.core.dtos.payout.response.AssignmentPayoutDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.helpers.MessagingAvailability;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.PaymentSessionResponseDto;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.Review;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring",
        uses = {JobMapper.class, UserMapper.class, CompanyMapper.class, PricingDtoMapper.class,
                MessagingAvailability.class, CancellationMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobAssignmentMapper {

    @Mapping(target = "messagingAvailable", source = "jobAssignment", qualifiedByName = "messagingAvailable")
    @Mapping(target = "messagingAvailableReason", source = "jobAssignment",
            qualifiedByName = "messagingAvailableReason")
    ShortJobAssignmentDto toShortJobAssignmentDto(JobAssignment jobAssignment);

    @Mapping(target = "pricing", ignore = true)
    PaymentSessionDto
            tojobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto(
            PaymentSessionResponseDto paymentSessionResponseDto);

    @Mapping(target = "id", source = "jobAssignment.id")
    @Mapping(target = "status", source = "jobAssignment.status")
    @Mapping(target = "createdAt", source = "jobAssignment.createdAt")
    @Mapping(target = "worker", source = "jobAssignment.worker")
    @Mapping(target = "totalCharged", source = "jobAssignment.totalCharged",
            conditionExpression = "java(!viewer.workerView())")
    @Mapping(target = "pricing", source = "jobAssignment", conditionExpression = "java(!viewer.workerView())")
    @Mapping(target = "myReview", source = "viewer.myReview")
    @Mapping(target = "counterpartyReviewed", source = "viewer.counterpartyReviewed")
    @Mapping(target = "messagingAvailable", source = "jobAssignment", qualifiedByName = "messagingAvailable")
    @Mapping(target = "messagingAvailableReason", source = "jobAssignment",
            qualifiedByName = "messagingAvailableReason")
    @Mapping(target = "employer", source = "jobAssignment.job.creator")
    @Mapping(target = "company", source = "jobAssignment.job.company")
    @Mapping(target = "application", source = "jobAssignment.application")
    @Mapping(target = "paymentSecuredAt", source = "paymentSecuredAt")
    @Mapping(target = "cancellation", source = "cancellation")
    @Mapping(target = "payout", source = "payout")
    @Mapping(target = "transport", source = "transport")
    MyAssignmentDto toMyAssignmentDto(JobAssignment jobAssignment, Cancellation cancellation, Payout payout,
                                      Instant paymentSecuredAt, ViewerFacts viewer, AssignmentTransportDto transport);

    MyReviewDto toMyReviewDto(Review review);

    AssignmentApplicationDto toAssignmentApplicationDto(JobApplication application);

    record ViewerFacts(boolean workerView, Review myReview, boolean counterpartyReviewed) {
    }

    AssignmentPayoutDto toAssignmentPayoutDto(Payout payout);

    @Mapping(target = "id", source = "jobAssignment.id")
    @Mapping(target = "jobId", source = "jobAssignment.job.id")
    @Mapping(target = "jobTitle", source = "jobAssignment.job.title")
    @Mapping(target = "companyId", source = "jobAssignment.job.company.id")
    @Mapping(target = "companyName", source = "jobAssignment.job.company.name")
    @Mapping(target = "worker", source = "worker")
    @Mapping(target = "employer", source = "jobAssignment.job.creator")
    @Mapping(target = "status", source = "jobAssignment.status")
    @Mapping(target = "paymentStatus", source = "jobAssignment.paymentStatus")
    @Mapping(target = "assignmentDateTime", source = "jobAssignment.assignmentDateTime")
    @Mapping(target = "actualCompletionDateTime", source = "jobAssignment.actualCompletionDateTime")
    @Mapping(target = "workHours", source = "jobAssignment.workHours")
    @Mapping(target = "pricing", source = "jobAssignment")
    AdminAssignmentDto toAdminAssignmentDto(JobAssignment jobAssignment, User worker);
}
