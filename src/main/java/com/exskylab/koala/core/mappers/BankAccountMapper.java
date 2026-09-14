package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.bankAccount.response.BankAccountDto;
import com.exskylab.koala.core.dtos.bankAccount.response.IbanPendingDto;
import com.exskylab.koala.core.dtos.bankAccount.response.IbanVerificationDto;
import com.exskylab.koala.core.helpers.BankAccountVerificationPolicy;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.entities.BankAccount;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        imports = {LogMasks.class, BankAccountVerificationPolicy.class, Instant.class})
public interface BankAccountMapper {

    @Mapping(target = "maskedIban", expression = "java(LogMasks.iban(account.getIban()))")
    @Mapping(target = "primary", source = "primaryAccount")
    @Mapping(target = "verificationStatus",
            expression = "java(BankAccountVerificationPolicy.effectiveStatus(account, Instant.now()))")
    BankAccountDto toBankAccountDto(BankAccount account);

    List<BankAccountDto> toBankAccountDtoList(List<BankAccount> accounts);

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "maskedIban", expression = "java(LogMasks.iban(account.getIban()))")
    @Mapping(target = "status",
            expression = "java(BankAccountVerificationPolicy.effectiveStatus(account, Instant.now()))")
    @Mapping(target = "reference", source = "account.verificationReference")
    @Mapping(target = "amount", source = "account.verificationAmount")
    @Mapping(target = "targetIban", source = "targetIban")
    @Mapping(target = "expiresAt", source = "account.verificationExpiresAt")
    @Mapping(target = "attemptsLeft", source = "attemptsLeft")
    IbanVerificationDto toIbanVerificationDto(BankAccount account, String targetIban, int attemptsLeft);

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "maskedIban", expression = "java(LogMasks.iban(account.getIban()))")
    @Mapping(target = "user", source = "account.user")
    @Mapping(target = "reference", source = "account.verificationReference")
    @Mapping(target = "amount", source = "account.verificationAmount")
    @Mapping(target = "startedAt", source = "account.verificationStartedAt")
    @Mapping(target = "expiresAt", source = "account.verificationExpiresAt")
    @Mapping(target = "status",
            expression = "java(BankAccountVerificationPolicy.effectiveStatus(account, Instant.now()))")
    @Mapping(target = "attemptsLeft",
            expression = "java(BankAccountVerificationPolicy.attemptsLeft(account, attemptsAllowed))")
    @Mapping(target = "createdAt", source = "account.createdAt")
    IbanPendingDto toIbanPendingDto(BankAccount account, int attemptsAllowed);
}
