package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.BankAccountMessages;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.dtos.bankAccount.request.SaveBankAccountDto;
import com.exskylab.koala.core.helpers.IbanValidator;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.BankAccountVerificationStatus;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class BankAccountManager implements BankAccountService {

    private final BankAccountDao bankAccountDao;
    private final SecurityService securityService;

    public BankAccountManager(BankAccountDao bankAccountDao, SecurityService securityService) {
        this.bankAccountDao = bankAccountDao;
        this.securityService = securityService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankAccount> getMyAccounts() {
        return bankAccountDao.findAllByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc(
                securityService.getAuthenticatedUserId());
    }

    @Override
    @Transactional
    public BankAccount addMyAccount(SaveBankAccountDto request) {
        User user = securityService.getAuthenticatedUser();
        bankAccountDao.lockUserAccounts(user.getId());
        String normalized = IbanValidator.normalize(request.getIban());

        if (bankAccountDao.findByUserIdAndIbanAndActiveTrue(user.getId(), normalized).isPresent()) {
            throw new ConflictException(BankAccountMessages.ALREADY_EXISTS);
        }

        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setIban(normalized);
        account.setPrimaryAccount(!bankAccountDao.existsByUserIdAndActiveTrue(user.getId()));
        account.setActive(true);
        account.setVerificationStatus(BankAccountVerificationStatus.UNVERIFIED);

        BankAccount saved = bankAccountDao.save(account);
        log.info("Bank account {} added for user {}", saved.getId(), user.getId());
        return saved;
    }

    @Override
    @Transactional
    public BankAccount selectMyPrimaryAccount(UUID accountId) {
        UUID userId = securityService.getAuthenticatedUserId();
        bankAccountDao.lockUserAccounts(userId);
        BankAccount account = requireOwned(accountId, userId);

        BankAccount saved = makePrimary(userId, account);
        log.info("Bank account {} selected as primary for user {}", accountId, userId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteMyAccount(UUID accountId) {
        UUID userId = securityService.getAuthenticatedUserId();
        bankAccountDao.lockUserAccounts(userId);
        bankAccountDao.delete(requireOwned(accountId, userId));
        log.info("Bank account {} deleted for user {}", accountId, userId);
    }

    @Override
    @Transactional
    public BankAccount savePrimaryAccount(User user, String iban) {
        if (!IbanValidator.isValid(iban)) {
            throw new ValidationException(UserMessages.INVALID_IBAN);
        }

        String normalized = IbanValidator.normalize(iban);
        bankAccountDao.lockUserAccounts(user.getId());

        Optional<BankAccount> matchingAccount = bankAccountDao
                .findByUserIdAndIbanAndActiveTrue(user.getId(), normalized);
        if (matchingAccount.isPresent()) {
            BankAccount saved = makePrimary(user.getId(), matchingAccount.get());
            log.info("Existing bank account {} selected as primary for user {}", saved.getId(), user.getId());
            return saved;
        }

        BankAccount account = bankAccountDao
                .findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId())
                .orElseGet(() -> {
                    BankAccount created = new BankAccount();
                    created.setUser(user);
                    created.setPrimaryAccount(true);
                    created.setActive(true);
                    created.setVerificationStatus(BankAccountVerificationStatus.UNVERIFIED);
                    return created;
                });

        account.setIban(normalized);
        account.setAccountHolderName(null);
        account.setVerificationStatus(BankAccountVerificationStatus.UNVERIFIED);

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

    private BankAccount requireOwned(UUID accountId, UUID userId) {
        return bankAccountDao.findByIdAndUserIdAndActiveTrue(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(BankAccountMessages.NOT_FOUND));
    }

    private BankAccount makePrimary(UUID userId, BankAccount account) {
        bankAccountDao.clearPrimary(userId);
        account.setPrimaryAccount(true);
        return bankAccountDao.save(account);
    }
}
