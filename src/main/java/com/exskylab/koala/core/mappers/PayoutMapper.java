package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.payout.response.PayoutDto;
import com.exskylab.koala.entities.Payout;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PayoutMapper {

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    PayoutDto toPayoutDto(Payout payout);

    List<PayoutDto> toPayoutDtoList(List<Payout> payouts);
}
