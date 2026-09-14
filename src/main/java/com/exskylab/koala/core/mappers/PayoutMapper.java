package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.payout.response.PayoutDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.User;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        imports = LogMasks.class)
public interface PayoutMapper {

    @Mapping(target = "jobAssignmentId", source = "jobAssignment.id")
    @Mapping(target = "jobTitle", source = "jobAssignment.job.title")
    @Mapping(target = "companyName", source = "jobAssignment.job.company.name")
    @Mapping(target = "maskedIban",
            expression = "java(payout.getBankAccount() == null ? null : LogMasks.iban(payout.getBankAccount().getIban()))")
    @Mapping(target = "bankAccountId", source = "bankAccount.id")
    @Mapping(target = "releasedAt", source = "createdAt")
    @Mapping(target = "settledAt", source = "completedAt")
    @Mapping(target = "amount", expression = "java(payout.getAmount() == null ? null : payout.getAmount().toPlainString())")
    PayoutDto toPayoutDto(Payout payout);

    List<PayoutDto> toPayoutDtoList(List<Payout> payouts);

    @Mapping(target = "id", source = "payout.id")
    @Mapping(target = "jobAssignmentId", source = "payout.jobAssignment.id")
    @Mapping(target = "recipient", source = "recipient")
    @Mapping(target = "maskedIban",
            expression = "java(payout.getBankAccount() == null ? null : LogMasks.iban(payout.getBankAccount().getIban()))")
    @Mapping(target = "providerReference", source = "payout.providerReference")
    @Mapping(target = "settlementReference", source = "payout.settlementReference")
    @Mapping(target = "amount", expression = "java(payout.getAmount() == null ? null : payout.getAmount().toPlainString())")
    @Mapping(target = "currency", source = "payout.currency")
    @Mapping(target = "status", source = "payout.status")
    @Mapping(target = "failureReason", source = "payout.failureReason")
    @Mapping(target = "processingAt", source = "payout.processingAt")
    @Mapping(target = "completedAt", source = "payout.completedAt")
    @Mapping(target = "createdAt", source = "payout.createdAt")
    StaffPayoutDto toStaffPayoutDto(Payout payout, User recipient);
}
