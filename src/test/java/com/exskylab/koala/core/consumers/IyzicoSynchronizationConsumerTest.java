package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import com.exskylab.koala.core.producers.user.UserUpdatedEventDto;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.SubMerchantUpdateRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IyzicoSynchronizationConsumerTest {

    private IyzicoPaymentService iyzicoPaymentService;
    private UserProfileSyncService syncService;
    private IyzicoSynchronizationConsumer consumer;

    @BeforeEach
    void setUp() {
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        syncService = mock(UserProfileSyncService.class);
        consumer = new IyzicoSynchronizationConsumer(iyzicoPaymentService, syncService);
    }

    @Test
    void aRedeliveredIntentDoesNotInvokeTheProviderAgain() {
        UserUpdatedEventDto event = event();
        when(syncService.claimForProcessing(event.getIntentId())).thenReturn(Optional.empty());

        consumer.handleUserProfileUpdatedForSync(event);

        verifyNoInteractions(iyzicoPaymentService);
        verify(syncService, never()).completeProcessing(any(), any());
    }

    @Test
    void aClaimedIntentUpdatesTheSubMerchantWithTheExactPayload() {
        UserUpdatedEventDto event = event();
        UUID claimId = UUID.randomUUID();
        when(syncService.claimForProcessing(event.getIntentId())).thenReturn(Optional.of(claimId));

        consumer.handleUserProfileUpdatedForSync(event);

        ArgumentCaptor<SubMerchantUpdateRequestDto> captor =
                ArgumentCaptor.forClass(SubMerchantUpdateRequestDto.class);
        verify(iyzicoPaymentService).updateSubMerchant(captor.capture());
        assertEquals("sub-merchant-key", captor.getValue().getSubMerchantKey());
        assertEquals("Ada Yilmaz", captor.getValue().getName());
        assertEquals("TR180006200119000006672315", captor.getValue().getIban());
        verify(syncService).completeProcessing(event.getIntentId(), claimId);
    }

    @Test
    void aLegacyEventWithoutIntentIdStillSynchronizes() {
        UserUpdatedEventDto event = event();
        event.setIntentId(null);

        consumer.handleUserProfileUpdatedForSync(event);

        verify(iyzicoPaymentService).updateSubMerchant(any());
        verify(syncService, never()).claimForProcessing(any());
    }

    @Test
    void aProviderFailureReleasesTheProcessingClaimSoRedeliveryCanRetry() {
        UserUpdatedEventDto event = event();
        UUID claimId = UUID.randomUUID();
        when(syncService.claimForProcessing(event.getIntentId())).thenReturn(Optional.of(claimId));
        doThrow(new IllegalStateException("provider")).when(iyzicoPaymentService).updateSubMerchant(any());

        assertThrows(IllegalStateException.class, () -> consumer.handleUserProfileUpdatedForSync(event));

        verify(syncService).releaseProcessingClaim(event.getIntentId(), claimId);
        verify(syncService, never()).completeProcessing(any(), any());
    }

    @Test
    void aUserWithoutASubMerchantKeyIsAcknowledgedWithoutCallingTheProvider() {
        UserUpdatedEventDto event = event();
        event.setIyzicoSubMerchantKey(null);
        UUID claimId = UUID.randomUUID();
        when(syncService.claimForProcessing(event.getIntentId())).thenReturn(Optional.of(claimId));

        consumer.handleUserProfileUpdatedForSync(event);

        verifyNoInteractions(iyzicoPaymentService);
        verify(syncService).completeProcessing(event.getIntentId(), claimId);
    }

    private UserUpdatedEventDto event() {
        UserUpdatedEventDto event = new UserUpdatedEventDto();
        event.setIntentId(UUID.randomUUID());
        event.setId(UUID.randomUUID());
        event.setFirstName("Ada");
        event.setLastName("Yilmaz");
        event.setEmail("ada@example.com");
        event.setGsmNumber("+905551112233");
        event.setAddress("Kucukbakkalkoy, No: 12");
        event.setIban("TR180006200119000006672315");
        event.setIdentityNumber("11111111110");
        event.setIyzicoSubMerchantKey("sub-merchant-key");
        return event;
    }
}
