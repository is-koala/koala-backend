package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.cancellation.response.CancellationDto;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.Refund;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CancellationMapper {

    @Mapping(target = "id", source = "cancellation.id")
    @Mapping(target = "jobAssignmentId", source = "cancellation.jobAssignment.id")
    @Mapping(target = "reason", source = "cancellation.reason")
    @Mapping(target = "refundStatus", source = "refund.status")
    @Mapping(target = "refundCompletedAt", source = "refund.completedAt")
    CancellationDto toCancellationDto(Cancellation cancellation, Refund refund);
}
