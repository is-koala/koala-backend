package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.cancellation.response.CancellationDto;
import com.exskylab.koala.entities.Cancellation;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CancellationMapper {

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    CancellationDto toCancellationDto(Cancellation cancellation);
}
