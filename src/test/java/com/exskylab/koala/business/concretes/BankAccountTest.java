package com.exskylab.koala.business.concretes;

import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        manager = new BankAccountManager(bankAccountDao);

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
        assertEquals("Ada Yilmaz", account.getAccountHolderName());
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
}
