package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.bankAccount.response.BankAccountDto;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.entities.BankAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = LogMasks.class)
public interface BankAccountMapper {

    @Mapping(target = "maskedIban", expression = "java(LogMasks.iban(account.getIban()))")
    @Mapping(target = "primary", source = "primaryAccount")
    BankAccountDto toBankAccountDto(BankAccount account);

    List<BankAccountDto> toBankAccountDtoList(List<BankAccount> accounts);
}
