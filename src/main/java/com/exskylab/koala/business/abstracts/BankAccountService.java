package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.bankAccount.request.ConfirmIbanTransferDto;
import com.exskylab.koala.core.dtos.bankAccount.request.SaveBankAccountDto;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    Optional<BankAccount> findVerifiedPrimaryAccount(UUID userId);

    BankAccount startMyVerification(UUID accountId);

    BankAccount confirmVerification(String reference, ConfirmIbanTransferDto observation);

    Page<BankAccount> listPendingForStaff(String query, UUID userId, Pageable pageable);

    List<BankAccount> getAccountsForStaff(UUID userId);

    int verificationAttemptsAllowed();
}
