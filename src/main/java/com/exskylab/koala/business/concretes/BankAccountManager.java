package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.helpers.IbanValidator;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.User;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class BankAccountManager implements BankAccountService {

    private final Logger logger = LoggerFactory.getLogger(BankAccountManager.class);

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
        logger.info("Primary bank account saved for user id: {}", user.getId());
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
