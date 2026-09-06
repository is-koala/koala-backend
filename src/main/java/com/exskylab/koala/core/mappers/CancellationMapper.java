package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.cancellation.response.CancellationDto;
import com.exskylab.koala.entities.Cancellation;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CancellationMapper {

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    @Mapping(target = "feeRate", expression = "java(com.exskylab.koala.core.helpers.PlainDecimalStringConverter.convert(cancellation.getFeeRate()))")
    @Mapping(target = "feeAmount", expression = "java(com.exskylab.koala.core.helpers.PlainDecimalStringConverter.convert(cancellation.getFeeAmount()))")
    @Mapping(target = "refundAmount", expression = "java(com.exskylab.koala.core.helpers.PlainDecimalStringConverter.convert(cancellation.getRefundAmount()))")
    @Mapping(target = "refundStatus", source = "refund.status")
    @Mapping(target = "refundCompletedAt", source = "refund.completedAt")
    CancellationDto toCancellationDto(Cancellation cancellation);
}
