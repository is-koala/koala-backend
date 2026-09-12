package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.payout.response.PayoutDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.entities.Payout;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        imports = LogMasks.class)
public interface PayoutMapper {

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    @Mapping(target = "amount", expression = "java(payout.getAmount() == null ? null : payout.getAmount().toPlainString())")
    PayoutDto toPayoutDto(Payout payout);

    List<PayoutDto> toPayoutDtoList(List<Payout> payouts);

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    @Mapping(target = "recipient", source = "worker")
    @Mapping(target = "maskedIban",
            expression = "java(payout.getBankAccount() == null ? null : LogMasks.iban(payout.getBankAccount().getIban()))")
    @Mapping(target = "amount", expression = "java(payout.getAmount() == null ? null : payout.getAmount().toPlainString())")
    StaffPayoutDto toStaffPayoutDto(Payout payout);

    List<StaffPayoutDto> toStaffPayoutDtoList(List<Payout> payouts);
}
