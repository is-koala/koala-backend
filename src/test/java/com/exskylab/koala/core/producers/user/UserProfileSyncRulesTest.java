package com.exskylab.koala.core.producers.user;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.concretes.BankAccountManager;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.properties.BankVerificationProperties;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.BankAccountVerificationStatus;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileSyncRulesTest {

    private static final String VERIFIED_IBAN = "TR180006200119000006672315";
    private static final String REPLACEMENT_IBAN = "TR330006100519786457841326";

    private BankAccountDao bankAccountDao;
    private RabbitTemplate rabbitTemplate;
    private UserProducerService producer;
    private User user;

    @BeforeEach
    void setUp() {
        bankAccountDao = mock(BankAccountDao.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        producer = new UserProducerService(rabbitTemplate, new BankAccountManager(
                bankAccountDao, mock(SecurityService.class), new BankVerificationProperties()));

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

        when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.empty());
    }

    @Test
    void anUnverifiedIbanIsWithheldFromTheProviderQueue() {
        givenPrimaryAccount(unverifiedAccount(VERIFIED_IBAN));

        assertNull(publishProfileUpdate().getIban());
    }

    @Test
    void aVerifiedIbanIsSynchronizedToTheProvider() {
        givenPrimaryAccount(verifiedAccount(VERIFIED_IBAN));

        assertEquals(VERIFIED_IBAN, publishProfileUpdate().getIban());
    }

    @Test
    void aNewIbanIsWithheldUntilItIsVerifiedItself() {
        BankAccount account = verifiedAccount(VERIFIED_IBAN);
        account.setIban(REPLACEMENT_IBAN);
        givenPrimaryAccount(account);

        assertNull(publishProfileUpdate().getIban());
    }

    @Test
    void aUserWithoutABankAccountSynchronizesWithoutAnIban() {
        assertNull(publishProfileUpdate().getIban());
    }

    @Test
    void theRestOfTheProfileIsSynchronizedEvenWhenTheIbanIsWithheld() {
        givenPrimaryAccount(unverifiedAccount(VERIFIED_IBAN));

        UserUpdatedEventDto event = publishProfileUpdate();

        assertNull(event.getIban());
        assertProfileFieldsPresent(event);
    }

    @Test
    void theRestOfTheProfileIsSynchronizedAlongsideAVerifiedIban() {
        givenPrimaryAccount(verifiedAccount(VERIFIED_IBAN));

        UserUpdatedEventDto event = publishProfileUpdate();

        assertEquals(VERIFIED_IBAN, event.getIban());
        assertProfileFieldsPresent(event);
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
        when(bankAccountDao.findByUserIdAndPrimaryAccountTrueAndActiveTrue(user.getId()))
                .thenReturn(Optional.of(account));
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
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.USER_EXCHANGE),
                eq(RabbitMQConfig.USER_PROFILE_UPDATED_ROUTING_KEY), captor.capture());
        return captor.getValue();
    }
}
