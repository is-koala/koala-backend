package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.helpers.IbanValidator;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class BankAccountManager implements BankAccountService {

    private final BankAccountDao bankAccountDao;

    public BankAccountManager(BankAccountDao bankAccountDao) {
        this.bankAccountDao = bankAccountDao;
    }

    @Override
    @Transactional
    public BankAccount savePrimaryAccount(User user, String iban) {
        if (!IbanValidator.isValid(iban)) {
            throw new ValidationException(UserMessages.INVALID_IBAN);
        }

        String normalized = IbanValidator.normalize(iban);

        BankAccount account = bankAccountDao
                .findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId())
                .orElseGet(() -> {
                    BankAccount created = new BankAccount();
                    created.setUser(user);
                    created.setPrimaryAccount(true);
                    created.setActive(true);
                    return created;
                });

        account.setIban(normalized);
        account.setAccountHolderName((user.getFirstName() + " " + user.getLastName()).trim());

        BankAccount saved = bankAccountDao.save(account);
        log.info("Primary bank account saved for user id: {}", user.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BankAccount> findPrimaryAccount(UUID userId) {
        return bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findPrimaryIban(UUID userId) {
        return findPrimaryAccount(userId).map(BankAccount::getIban);
    }
}
