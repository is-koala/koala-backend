package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.BankAccountMessages;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.dtos.bankAccount.request.ConfirmIbanTransferDto;
import com.exskylab.koala.core.dtos.bankAccount.request.SaveBankAccountDto;
import com.exskylab.koala.core.helpers.BankAccountVerificationPolicy;
import com.exskylab.koala.core.helpers.IbanValidator;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.properties.BankVerificationProperties;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.BankAccountVerificationStatus;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.StaffSearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class BankAccountManager implements BankAccountService {

    private static final String REFERENCE_PREFIX = "KOALA-";
    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REFERENCE_LENGTH = 8;
    private static final int REFERENCE_ATTEMPTS = 5;

    private static final Set<String> PENDING_SORTABLE = Set.of("createdAt", "verificationStartedAt");

    private static final Sort PENDING_SORT = Sort.by(Sort.Direction.DESC, "verificationStartedAt");

    private final BankAccountDao bankAccountDao;
    private final SecurityService securityService;
    private final BankVerificationProperties verificationProperties;
    private final PageableSanitizer pageableSanitizer;
    private final SecureRandom random = new SecureRandom();

    public BankAccountManager(BankAccountDao bankAccountDao, SecurityService securityService,
                              BankVerificationProperties verificationProperties,
                              PageableSanitizer pageableSanitizer) {
        this.bankAccountDao = bankAccountDao;
        this.securityService = securityService;
        this.verificationProperties = verificationProperties;
        this.pageableSanitizer = pageableSanitizer;
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
                    return created;
                });

        account.setIban(normalized);

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

    @Override
    @Transactional(readOnly = true)
    public Optional<BankAccount> findVerifiedPrimaryAccount(UUID userId) {
        return findPrimaryAccount(userId).filter(BankAccountVerificationPolicy::isVerified);
    }

    @Override
    @Transactional
    public BankAccount startMyVerification(UUID accountId) {
        UUID userId = securityService.getAuthenticatedUserId();
        bankAccountDao.lockUserAccounts(userId);
        BankAccount account = requireOwned(accountId, userId);
        Instant now = Instant.now();

        requireCollectionAccount();

        BankAccountVerificationStatus status = BankAccountVerificationPolicy.effectiveStatus(account, now);
        if (status == BankAccountVerificationStatus.VERIFIED) {
            throw new ConflictException(BankAccountMessages.ALREADY_VERIFIED);
        }
        if (status == BankAccountVerificationStatus.PENDING) {
            log.info("Bank account {} already awaits a verification transfer for user {}", accountId, userId);
            return account;
        }
        if (account.getVerificationAttempts() >= verificationProperties.getMaxAttempts()) {
            throw new ConflictException(BankAccountMessages.ATTEMPT_LIMIT_REACHED);
        }

        account.setVerificationAttempts(account.getVerificationAttempts() + 1);
        account.setVerificationReference(nextReference());
        account.setVerificationAmount(nextAmount());
        account.setVerificationStartedAt(now);
        account.setVerificationExpiresAt(now.plus(verificationProperties.getValidityHours(), ChronoUnit.HOURS));
        account.setVerificationStatus(BankAccountVerificationStatus.PENDING);

        BankAccount saved = bankAccountDao.save(account);
        log.info("Verification transfer {} expected for bank account {} of user {}",
                saved.getVerificationReference(), saved.getId(), userId);
        return saved;
    }

    @Override
    @Transactional(noRollbackFor = ValidationException.class)
    public BankAccount confirmVerification(String reference, ConfirmIbanTransferDto observation) {
        BankAccount account = bankAccountDao
                .findByVerificationReferenceAndActiveTrue(reference)
                .orElseThrow(() -> new ResourceNotFoundException(BankAccountMessages.VERIFICATION_NOT_FOUND));

        Instant now = Instant.now();
        BankAccountVerificationStatus status = BankAccountVerificationPolicy.effectiveStatus(account, now);
        if (status == BankAccountVerificationStatus.VERIFIED) {
            throw new ConflictException(BankAccountMessages.ALREADY_VERIFIED);
        }
        if (status != BankAccountVerificationStatus.PENDING) {
            throw new ValidationException(BankAccountMessages.VERIFICATION_EXPIRED);
        }

        String sender = IbanValidator.normalize(observation.getSenderIban());
        if (!account.getIban().equals(sender)) {
            log.warn("Verification transfer {} rejected for bank account {}: the sender account is not the "
                    + "registered one", reference, account.getId());
            throw abandon(account, BankAccountMessages.SENDER_IBAN_MISMATCH);
        }

        if (account.getVerificationAmount().compareTo(observation.getAmount()) != 0) {
            log.warn("Verification transfer {} rejected for bank account {}: the amount does not match",
                    reference, account.getId());
            throw abandon(account, BankAccountMessages.AMOUNT_MISMATCH);
        }

        account.setVerificationStatus(BankAccountVerificationStatus.VERIFIED);
        account.setVerifiedAt(now);

        String senderName = observation.getSenderName() == null ? null : observation.getSenderName().trim();
        if (senderName != null && !senderName.isEmpty()) {
            account.setAccountHolderName(senderName);
            account.setAccountHolderNameVerified(true);
        } else {
            account.setAccountHolderName(null);
            account.setAccountHolderNameVerified(false);
        }

        BankAccount saved = bankAccountDao.save(account);
        log.info("Bank account {} verified through transfer {} for user {}",
                saved.getId(), reference, saved.getUser().getId());
        return saved;
    }

    private ValidationException abandon(BankAccount account, String message) {
        account.setVerificationStatus(BankAccountVerificationStatus.EXPIRED);
        bankAccountDao.save(account);
        return new ValidationException(message);
    }

    private void requireCollectionAccount() {
        String collectionIban = verificationProperties.getCollectionIban();
        if (collectionIban == null || collectionIban.isBlank()) {
            log.error("Bank account verification is unavailable: no collection account is configured");
            throw new ExternalServiceException(BankAccountMessages.VERIFICATION_UNAVAILABLE);
        }
    }

    private String nextReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            StringBuilder reference = new StringBuilder(REFERENCE_PREFIX);
            for (int index = 0; index < REFERENCE_LENGTH; index++) {
                reference.append(REFERENCE_ALPHABET.charAt(random.nextInt(REFERENCE_ALPHABET.length())));
            }
            String candidate = reference.toString();
            if (bankAccountDao.findByVerificationReferenceAndActiveTrue(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ExternalServiceException(BankAccountMessages.VERIFICATION_UNAVAILABLE);
    }

    private BigDecimal nextAmount() {
        long minimum = verificationProperties.getMinimumAmount().movePointRight(2).longValueExact();
        long maximum = verificationProperties.getMaximumAmount().movePointRight(2).longValueExact();
        if (minimum <= 0 || maximum < minimum) {
            log.error("Bank account verification is unavailable: the configured amount range is empty");
            throw new ExternalServiceException(BankAccountMessages.VERIFICATION_UNAVAILABLE);
        }
        long minorUnits = minimum + (long) random.nextInt((int) (maximum - minimum + 1));
        return BigDecimal.valueOf(minorUnits, 2);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BankAccount> listPendingForStaff(String query, UUID userId, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, PENDING_SORTABLE);
        return bankAccountDao.findAwaitingStaffConfirmation(Instant.now(), StaffSearchQuery.normalize(query), userId,
                pageableSanitizer.sanitize(requested, PENDING_SORTABLE, PENDING_SORT));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankAccount> getAccountsForStaff(UUID userId) {
        return bankAccountDao.findAllByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc(userId);
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

    @Override
    public int verificationAttemptsAllowed() {
        return verificationProperties.getMaxAttempts();
    }
}
