package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobAssignment.response.PaymentSessionDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.JobAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.ShortJobAssignmentDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.PaymentSessionResponseDto;
import com.exskylab.koala.entities.JobAssignment;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring",
        uses = {JobMapper.class, UserMapper.class, CompanyMapper.class, PricingDtoMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface JobAssignmentMapper {

    ShortJobAssignmentDto toShortJobAssignmentDto(JobAssignment jobAssignment);

    @Mapping(target = "company", source = "job.company")
    JobAssignmentDto toJobAssignmentsAssignmentIdStatusPatchResponseDto(
            JobAssignment jobAssignment);

    @Mapping(target = "pricing", ignore = true)
    PaymentSessionDto
            tojobAssignmentsAssignmentIdPaymentSessionInitiatePostResponseDto(
            PaymentSessionResponseDto paymentSessionResponseDto);

    @Mapping(target = "pricing", source = "jobAssignment")
    MyAssignmentDto toMyAssignmentDto(JobAssignment jobAssignment);

    java.util.List<MyAssignmentDto> toMyAssignmentDtoList(java.util.List<JobAssignment> assignments);
}
