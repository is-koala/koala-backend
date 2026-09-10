package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.PushDeliveryService;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PushConsumerTest {

    @Test
    void queuePayloadIsOnlyATriggerAndItsTokenIsIgnored() {
        PushDeliveryService deliveryService = mock(PushDeliveryService.class);
        PushConsumer consumer = new PushConsumer(deliveryService);
        UUID id = UUID.randomUUID();

        consumer.handlePushTask(new PushTaskDto(id));

        verify(deliveryService).deliver(id);
    }

    @Test
    void unexpectedFailureEscapesToTheListenerRetryAdvice() {
        PushDeliveryService deliveryService = mock(PushDeliveryService.class);
        PushConsumer consumer = new PushConsumer(deliveryService);
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("boom")).when(deliveryService).deliver(id);

        assertThrows(IllegalStateException.class, () -> consumer.handlePushTask(
                new PushTaskDto(id)));
    }

    @Test
    void missingNotificationIdEscapesToTheListenerRetryAndDlqPath() throws Exception {
        PushDeliveryService deliveryService = mock(PushDeliveryService.class);
        PushConsumer consumer = new PushConsumer(deliveryService);
        PushTaskDto missingId = new ObjectMapper().readValue("{}", PushTaskDto.class);

        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> consumer.handlePushTask(missingId));

        org.junit.jupiter.api.Assertions.assertEquals(
                "Push task notification ID is required", failure.getMessage());
        verifyNoInteractions(deliveryService);
    }
}
