package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.properties.BankVerificationProperties;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.BankAccountVerificationStatus;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankAccountTest {

    private BankAccountDao bankAccountDao;
    private BankAccountManager manager;
    private User user;

    @BeforeEach
    void setUp() {
        bankAccountDao = mock(BankAccountDao.class);
        manager = new BankAccountManager(bankAccountDao, mock(SecurityService.class),
                new BankVerificationProperties(), new com.exskylab.koala.core.helpers.PageableSanitizer());

        user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");

        when(bankAccountDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.empty());
    }

    @Test
    void aValidIbanIsStoredNormalisedAsThePrimaryAccount() {
        BankAccount account = manager.savePrimaryAccount(user, "TR18 0006 2001 1900 0006 6723 15");

        assertEquals("TR180006200119000006672315", account.getIban());
        assertNull(account.getAccountHolderName());
        assertTrue(account.isPrimaryAccount());
        assertTrue(account.isActive());
    }

    @Test
    void anInvalidIbanIsRejectedBeforeAnythingIsStored() {
        assertThrows(ValidationException.class, () -> manager.savePrimaryAccount(user, "TR00 bozuk"));
        assertThrows(ValidationException.class, () -> manager.savePrimaryAccount(user, null));

        verify(bankAccountDao, never()).save(any());
    }

    @Test
    void savingAgainUpdatesTheExistingPrimaryAccountInsteadOfAddingAnother() {
        BankAccount existing = new BankAccount();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setPrimaryAccount(true);
        existing.setActive(true);
        existing.setIban("TR960001000200300040005006");

        when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.of(existing));

        BankAccount updated = manager.savePrimaryAccount(user, "TR180006200119000006672315");

        assertEquals(existing.getId(), updated.getId(), "Yeni kayit acilmamaliydi.");
        assertEquals("TR180006200119000006672315", updated.getIban());
    }

    @Test
    void writingANewIbanOverAVerifiedOneDropsTheVerification() {
        BankAccount existing = verifiedAccount("TR960001000200300040005006");

        when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.of(existing));

        BankAccount updated = manager.savePrimaryAccount(user, "TR180006200119000006672315");

        assertEquals("TR180006200119000006672315", updated.getIban());
        assertEquals(BankAccountVerificationStatus.UNVERIFIED, updated.getVerificationStatus(),
                "Dogrulanmis IBAN'in ustune yazan yeni IBAN dogrulanmis sayilamaz.");
        assertNull(updated.getVerifiedAt());
        assertNull(updated.getVerificationReference());
        assertNull(updated.getVerificationAmount());
        assertNull(updated.getVerificationExpiresAt());
        assertEquals(0, updated.getVerificationAttempts());
        assertNull(updated.getAccountHolderName());
        assertFalse(updated.isAccountHolderNameVerified());
    }

    @Test
    void resendingTheSameIbanKeepsTheVerification() {
        BankAccount existing = verifiedAccount("TR180006200119000006672315");

        when(bankAccountDao.findByUserIdAndIbanAndActiveTrue(user.getId(), "TR180006200119000006672315"))
                .thenReturn(Optional.of(existing));

        BankAccount updated = manager.savePrimaryAccount(user, "TR18 0006 2001 1900 0006 6723 15");

        assertEquals(BankAccountVerificationStatus.VERIFIED, updated.getVerificationStatus());
    }

    @Test
    void theIbanIsReadBackThroughTheAccountNotTheUser() {
        BankAccount account = new BankAccount();
        account.setIban("TR180006200119000006672315");
        when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.of(account));

        assertEquals(Optional.of("TR180006200119000006672315"), manager.findPrimaryIban(user.getId()));
    }

    @Test
    void aUserWithoutAnAccountReportsNoIban() {
        assertEquals(Optional.empty(), manager.findPrimaryIban(user.getId()));
    }

    @Test
    void onlyAVerifiedPrimaryAccountIsReportedAsVerified() {
        BankAccount unverified = new BankAccount();
        unverified.setIban("TR180006200119000006672315");
        when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.of(unverified));

        assertEquals(Optional.empty(), manager.findVerifiedPrimaryAccount(user.getId()));

        unverified.setVerificationStatus(BankAccountVerificationStatus.VERIFIED);

        assertEquals(Optional.of(unverified), manager.findVerifiedPrimaryAccount(user.getId()));
    }

    private BankAccount verifiedAccount(String iban) {
        BankAccount existing = new BankAccount();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setPrimaryAccount(true);
        existing.setActive(true);
        existing.setIban(iban);
        existing.setVerificationStatus(BankAccountVerificationStatus.VERIFIED);
        existing.setVerifiedAt(Instant.now());
        existing.setVerificationReference("KOALA-ABCDEFGH");
        existing.setVerificationAmount(new BigDecimal("1.42"));
        existing.setVerificationExpiresAt(Instant.now());
        existing.setVerificationAttempts(1);
        existing.setAccountHolderName("Ada Yilmaz");
        existing.setAccountHolderNameVerified(true);
        return existing;
    }
}
