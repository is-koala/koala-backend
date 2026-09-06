package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.bankAccount.request.SaveBankAccountDto;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountService {

    List<BankAccount> getMyAccounts();

    BankAccount addMyAccount(SaveBankAccountDto request);

    BankAccount selectMyPrimaryAccount(UUID accountId);

    void deleteMyAccount(UUID accountId);

    BankAccount savePrimaryAccount(User user, String iban);

    Optional<BankAccount> findPrimaryAccount(UUID userId);

    Optional<String> findPrimaryIban(UUID userId);
}
