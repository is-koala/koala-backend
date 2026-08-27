package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.User;

import java.util.Optional;
import java.util.UUID;

public interface BankAccountService {

    BankAccount savePrimaryAccount(User user, String iban);

    Optional<BankAccount> findPrimaryAccount(UUID userId);

    Optional<String> findPrimaryIban(UUID userId);
}
