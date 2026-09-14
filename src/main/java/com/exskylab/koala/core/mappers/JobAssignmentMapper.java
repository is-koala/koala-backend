package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.PaymentSessionDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.JobAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.ShortJobAssignmentDto;
import com.exskylab.koala.core.helpers.MessagingAvailability;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.PaymentSessionResponseDto;
import com.exskylab.koala.entities.JobAssignment;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring",
        uses = {JobMapper.class, UserMapper.class, CompanyMapper.class, PricingDtoMapper.class,
                MessagingAvailability.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobAssignmentMapper {

    @Mapping(target = "messagingAvailable", source = "jobAssignment", qualifiedByName = "messagingAvailable")
    @Mapping(target = "messagingAvailableReason", source = "jobAssignment",
            qualifiedByName = "messagingAvailableReason")
    ShortJobAssignmentDto toShortJobAssignmentDto(JobAssignment jobAssignment);

    @Mapping(target = "company", source = "job.company")
    @Mapping(target = "messagingAvailable", source = "jobAssignment", qualifiedByName = "messagingAvailable")
    @Mapping(target = "messagingAvailableReason", source = "jobAssignment",
            qualifiedByName = "messagingAvailableReason")
    JobAssignmentDto toJobAssignmentsAssignmentIdStatusPatchResponseDto(
            JobAssignment jobAssignment);

    @Mapping(target = "pricing", ignore = true)
    PaymentSessionDto
            tojobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto(
            PaymentSessionResponseDto paymentSessionResponseDto);

    @Mapping(target = "pricing", source = "jobAssignment")
    @Mapping(target = "messagingAvailable", source = "jobAssignment", qualifiedByName = "messagingAvailable")
    @Mapping(target = "messagingAvailableReason", source = "jobAssignment",
            qualifiedByName = "messagingAvailableReason")
    MyAssignmentDto toMyAssignmentDto(JobAssignment jobAssignment);

    java.util.List<MyAssignmentDto> toMyAssignmentDtoList(java.util.List<JobAssignment> assignments);

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "jobTitle", source = "job.title")
    @Mapping(target = "companyName", source = "job.company.name")
    @Mapping(target = "employer", source = "job.creator")
    @Mapping(target = "pricing", source = "jobAssignment")
    AdminAssignmentDto toAdminAssignmentDto(JobAssignment jobAssignment);
}
