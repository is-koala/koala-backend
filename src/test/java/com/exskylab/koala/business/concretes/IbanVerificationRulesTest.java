package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.bankAccount.request.ConfirmIbanTransferDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.BankVerificationProperties;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.BankAccountVerificationStatus;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IbanVerificationRulesTest {

    private static final String OWN_IBAN = "TR180006200119000006672315";
    private static final String SOMEONE_ELSES_IBAN = "TR960001000200300040005006";

    private BankAccountDao bankAccountDao;
    private BankVerificationProperties properties;
    private BankAccountManager manager;
    private BankAccount account;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bankAccountDao = mock(BankAccountDao.class);
        SecurityService securityService = mock(SecurityService.class);
        properties = new BankVerificationProperties();
        properties.setCollectionIban("TR330006100519786457841326");

        manager = new BankAccountManager(bankAccountDao, securityService, properties);

        User user = new User();
        user.setId(userId);

        account = new BankAccount();
        account.setId(accountId);
        account.setUser(user);
        account.setIban(OWN_IBAN);
        account.setPrimaryAccount(true);
        account.setActive(true);

        when(securityService.getAuthenticatedUserId()).thenReturn(userId);
        when(bankAccountDao.findByIdAndUserIdAndActiveTrue(accountId, userId)).thenReturn(Optional.of(account));
        when(bankAccountDao.findByVerificationReferenceAndActiveTrue(anyString())).thenReturn(Optional.empty());
        when(bankAccountDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void referenceResolves() {
        when(bankAccountDao.findByVerificationReferenceAndActiveTrue(account.getVerificationReference()))
                .thenReturn(Optional.of(account));
    }

    private ConfirmIbanTransferDto statement(String senderIban, BigDecimal amount, String senderName) {
        ConfirmIbanTransferDto observation = new ConfirmIbanTransferDto();
        observation.setSenderIban(senderIban);
        observation.setAmount(amount);
        observation.setSenderName(senderName);
        return observation;
    }

    @Test
    void startingAVerificationAsksForASmallTransferCarryingAUniqueReference() {
        BankAccount started = manager.startMyVerification(accountId);

        assertEquals(BankAccountVerificationStatus.PENDING, started.getVerificationStatus());
        assertNotNull(started.getVerificationReference());
        assertTrue(started.getVerificationReference().startsWith("KOALA-"));
        assertEquals(2, started.getVerificationAmount().scale(), "Tutar kurus olcegini korumali.");
        assertTrue(started.getVerificationAmount().signum() > 0);
        assertNotNull(started.getVerificationExpiresAt());
        assertEquals(1, started.getVerificationAttempts());
    }

    @Test
    void askingAgainWhileTheTransferIsAwaitedRepeatsTheSameInstructions() {
        BankAccount first = manager.startMyVerification(accountId);
        String reference = first.getVerificationReference();
        BigDecimal amount = first.getVerificationAmount();

        BankAccount second = manager.startMyVerification(accountId);

        assertEquals(reference, second.getVerificationReference());
        assertEquals(amount, second.getVerificationAmount());
        assertEquals(1, second.getVerificationAttempts(), "Tekrar sorma yeni bir deneme harcamamali.");
    }

    @Test
    void aTransferSentFromAnotherAccountDoesNotVerifyTheIban() {
        manager.startMyVerification(accountId);
        referenceResolves();

        assertThrows(ValidationException.class, () -> manager.confirmVerification(
                account.getVerificationReference(),
                statement(SOMEONE_ELSES_IBAN, account.getVerificationAmount(), null)));

        assertNotEquals(BankAccountVerificationStatus.VERIFIED, account.getVerificationStatus());
        assertNull(account.getVerifiedAt());
    }

    @Test
    void aTransferCarryingTheWrongAmountDoesNotVerifyTheIban() {
        manager.startMyVerification(accountId);
        referenceResolves();

        assertThrows(ValidationException.class, () -> manager.confirmVerification(
                account.getVerificationReference(),
                statement(OWN_IBAN, account.getVerificationAmount().add(new BigDecimal("0.01")), null)));

        assertNotEquals(BankAccountVerificationStatus.VERIFIED, account.getVerificationStatus());
    }

    @Test
    void aTransferFromTheRegisteredAccountVerifiesTheIban() {
        manager.startMyVerification(accountId);
        referenceResolves();

        BankAccount verified = manager.confirmVerification(
                account.getVerificationReference(),
                statement(OWN_IBAN, account.getVerificationAmount(), null));

        assertEquals(BankAccountVerificationStatus.VERIFIED, verified.getVerificationStatus());
        assertNotNull(verified.getVerifiedAt());
    }

    @Test
    void withoutANameOnTheStatementTheAccountHolderNameStaysUnverified() {
        manager.startMyVerification(accountId);
        referenceResolves();

        BankAccount verified = manager.confirmVerification(
                account.getVerificationReference(),
                statement(OWN_IBAN, account.getVerificationAmount(), "  "));

        assertNull(verified.getAccountHolderName());
        assertFalse(verified.isAccountHolderNameVerified());
    }

    @Test
    void aNameReadFromTheStatementIsStoredAsVerified() {
        manager.startMyVerification(accountId);
        referenceResolves();

        BankAccount verified = manager.confirmVerification(
                account.getVerificationReference(),
                statement(OWN_IBAN, account.getVerificationAmount(), " Ada Yilmaz "));

        assertEquals("Ada Yilmaz", verified.getAccountHolderName());
        assertTrue(verified.isAccountHolderNameVerified());
    }

    @Test
    void anExpiredVerificationCanBeStartedAgainWithFreshInstructions() {
        BankAccount first = manager.startMyVerification(accountId);
        String staleReference = first.getVerificationReference();
        account.setVerificationExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        BankAccount restarted = manager.startMyVerification(accountId);

        assertEquals(BankAccountVerificationStatus.PENDING, restarted.getVerificationStatus());
        assertNotEquals(staleReference, restarted.getVerificationReference());
        assertTrue(restarted.getVerificationExpiresAt().isAfter(Instant.now()));
        assertEquals(2, restarted.getVerificationAttempts());
    }

    @Test
    void aTransferArrivingAfterTheDeadlineIsRefused() {
        manager.startMyVerification(accountId);
        referenceResolves();
        account.setVerificationExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThrows(ValidationException.class, () -> manager.confirmVerification(
                account.getVerificationReference(),
                statement(OWN_IBAN, account.getVerificationAmount(), null)));

        assertNotEquals(BankAccountVerificationStatus.VERIFIED, account.getVerificationStatus());
    }

    @Test
    void theAttemptBudgetRunsOut() {
        properties.setMaxAttempts(2);

        manager.startMyVerification(accountId);
        account.setVerificationExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        manager.startMyVerification(accountId);
        account.setVerificationExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThrows(ConflictException.class, () -> manager.startMyVerification(accountId));
    }

    @Test
    void anAlreadyVerifiedAccountIsNotSentBackToTheBank() {
        manager.startMyVerification(accountId);
        referenceResolves();
        manager.confirmVerification(account.getVerificationReference(),
                statement(OWN_IBAN, account.getVerificationAmount(), null));

        assertThrows(ConflictException.class, () -> manager.startMyVerification(accountId));
    }

    @Test
    void anUnknownReferenceIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> manager.confirmVerification(
                "KOALA-ZZZZZZZZ", statement(OWN_IBAN, new BigDecimal("1.01"), null)));
    }

    @Test
    void verificationCannotStartWhileNoCollectionAccountIsConfigured() {
        properties.setCollectionIban("  ");

        assertThrows(ExternalServiceException.class, () -> manager.startMyVerification(accountId));
        assertEquals(BankAccountVerificationStatus.UNVERIFIED, account.getVerificationStatus());
    }
}
