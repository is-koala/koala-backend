package com.exskylab.koala.core.producers.user;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import com.exskylab.koala.business.concretes.BankAccountManager;
import com.exskylab.koala.core.properties.BankVerificationProperties;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.BankAccountVerificationStatus;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class UserProfileSyncRulesTest {

    private static final String VERIFIED_IBAN = "TR180006200119000006672315";
    private static final String REPLACEMENT_IBAN = "TR330006100519786457841326";

    private BankAccountDao bankAccountDao;
    private UserProfileSyncService syncService;
    private UserProducerService producer;
    private User user;

    @BeforeEach
    void setUp() {
        bankAccountDao = mock(BankAccountDao.class);
        syncService = mock(UserProfileSyncService.class);
        producer = new UserProducerService(syncService, new BankAccountManager(
                bankAccountDao, mock(SecurityService.class), new BankVerificationProperties(),
                new com.exskylab.koala.core.helpers.PageableSanitizer()));

        Address address = new Address();
        address.setNeighborhood("Kucukbakkalkoy");
        address.setBuildingNumber("12");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");
        user.setEmail("ada@example.com");
        user.setPhoneNumber("+905551112233");
        user.setAddress(address);
        user.setTcIdentityNumber("11111111110");
        user.setIyzicoSubmerchantKey("sub-merchant-key");

        whenEmptyAccount();
    }

    @Test
    void anUnverifiedIbanIsSynchronizedToTheProvider() {
        givenPrimaryAccount(unverifiedAccount(VERIFIED_IBAN));

        assertEquals(VERIFIED_IBAN, publishProfileUpdate().getIban());
    }

    @Test
    void aVerifiedIbanIsSynchronizedToTheProvider() {
        givenPrimaryAccount(verifiedAccount(VERIFIED_IBAN));

        assertEquals(VERIFIED_IBAN, publishProfileUpdate().getIban());
    }

    @Test
    void aNewIbanIsSynchronizedEvenBeforeOwnershipIsProven() {
        BankAccount account = verifiedAccount(VERIFIED_IBAN);
        account.setIban(REPLACEMENT_IBAN);
        givenPrimaryAccount(account);

        assertEquals(REPLACEMENT_IBAN, publishProfileUpdate().getIban());
    }

    @Test
    void aUserWithoutABankAccountSynchronizesWithoutAnIban() {
        assertNull(publishProfileUpdate().getIban());
    }

    @Test
    void theRestOfTheProfileIsSynchronizedAlongsideThePrimaryIban() {
        givenPrimaryAccount(unverifiedAccount(VERIFIED_IBAN));

        UserUpdatedEventDto event = publishProfileUpdate();

        assertEquals(VERIFIED_IBAN, event.getIban());
        assertProfileFieldsPresent(event);
    }

    @Test
    void theRestOfTheProfileIsSynchronizedAlongsideAVerifiedIban() {
        givenPrimaryAccount(verifiedAccount(VERIFIED_IBAN));

        UserUpdatedEventDto event = publishProfileUpdate();

        assertEquals(VERIFIED_IBAN, event.getIban());
        assertProfileFieldsPresent(event);
    }

    @Test
    void aProfileUpdateAcceptsTheSyncIntentWithoutTalkingToTheBroker() {
        publishProfileUpdate();

        verifyNoMoreInteractions(syncService);
    }

    private void assertProfileFieldsPresent(UserUpdatedEventDto event) {
        assertEquals(user.getId(), event.getId());
        assertEquals("Ada", event.getFirstName());
        assertEquals("Yilmaz", event.getLastName());
        assertEquals("ada@example.com", event.getEmail());
        assertEquals("+905551112233", event.getGsmNumber());
        assertEquals("Kucukbakkalkoy, No: 12", event.getAddress());
        assertEquals("11111111110", event.getIdentityNumber());
        assertEquals("sub-merchant-key", event.getIyzicoSubMerchantKey());
    }

    private void givenPrimaryAccount(BankAccount account) {
        org.mockito.Mockito.when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.of(account));
    }

    private void whenEmptyAccount() {
        org.mockito.Mockito.when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.empty());
    }

    private BankAccount unverifiedAccount(String iban) {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setUser(user);
        account.setIban(iban);
        account.setPrimaryAccount(true);
        account.setActive(true);
        return account;
    }

    private BankAccount verifiedAccount(String iban) {
        BankAccount account = unverifiedAccount(iban);
        account.setVerificationStatus(BankAccountVerificationStatus.VERIFIED);
        return account;
    }

    private UserUpdatedEventDto publishProfileUpdate() {
        producer.publishUserProfileUpdated(user);

        ArgumentCaptor<UserUpdatedEventDto> captor = ArgumentCaptor.forClass(UserUpdatedEventDto.class);
        verify(syncService).enqueue(captor.capture());
        return captor.getValue();
    }
}
