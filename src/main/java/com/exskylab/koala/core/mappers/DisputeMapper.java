package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.entities.Dispute;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DisputeMapper {

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    DisputeDto toDisputeDto(Dispute dispute);

    List<DisputeDto> toDisputeDtoList(List<Dispute> disputes);
}
