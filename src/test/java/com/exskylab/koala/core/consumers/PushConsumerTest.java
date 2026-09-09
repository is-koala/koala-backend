package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import com.exskylab.koala.core.utilities.push.PushService;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

class PushConsumerTest {

    @Test
    void aProviderCallReceivesTheBoundedDeliveryDeadline() {
        UUID id = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        NotificationService notifications = mock(NotificationService.class);
        PushService pushService = mock(PushService.class);
        when(notifications.getById(id)).thenReturn(notification(id, NotificationStatus.QUEUED));
        when(notifications.claimForDelivery(id, NotificationChannel.PUSH)).thenReturn(Optional.of(claimId));
        when(pushService.sendPush("push-token", "Baslik", "Gövde", Map.of("jobId", "job-7"),
                Duration.ofSeconds(45))).thenReturn("message-7");

        consumer(notifications, pushService).handlePushTask(task(id));

        verify(pushService).sendPush("push-token", "Baslik", "Gövde", Map.of("jobId", "job-7"),
                Duration.ofSeconds(45));
    }

    @Test
    void aMissingNotificationRowIsDroppedWithoutCallingTheProvider() {
        UUID id = UUID.randomUUID();
        NotificationService notifications = mock(NotificationService.class);
        PushService pushService = mock(PushService.class);
        when(notifications.getById(id)).thenThrow(new ResourceNotFoundException("yok"));

        PushConsumer consumer = consumer(notifications, pushService);

        assertDoesNotThrow(() -> consumer.handlePushTask(task(id)));
        verifyNoInteractions(pushService);
    }

    @Test
    void aTransientNotificationLookupFailureEscapesSoRabbitCanRetry() {
        UUID id = UUID.randomUUID();
        NotificationService notifications = mock(NotificationService.class);
        PushService pushService = mock(PushService.class);
        when(notifications.getById(id))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        PushConsumer consumer = consumer(notifications, pushService);

        assertThrows(DataAccessResourceFailureException.class, () -> consumer.handlePushTask(task(id)));
        verifyNoInteractions(pushService);
    }

    @Test
    void duplicateDeliveryCannotInvokeTheProviderAgainAfterSuccess() {
        UUID id = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        Notification notification = notification(id, NotificationStatus.QUEUED);
        NotificationService notifications = mock(NotificationService.class);
        PushService pushService = mock(PushService.class);
        when(notifications.getById(id)).thenReturn(notification);
        when(notifications.claimForDelivery(id, NotificationChannel.PUSH))
                .thenReturn(Optional.of(claimId), Optional.empty());
        when(pushService.sendPush("push-token", "Baslik", "Gövde", Map.of("jobId", "job-7"),
                Duration.ofSeconds(45)))
                .thenReturn("message-7");

        PushConsumer consumer = consumer(notifications, pushService);
        consumer.handlePushTask(task(id));
        consumer.handlePushTask(task(id));

        verify(pushService, times(1)).sendPush("push-token", "Baslik", "Gövde", Map.of("jobId", "job-7"),
                Duration.ofSeconds(45));
        verify(notifications).completeDelivery(id, NotificationChannel.PUSH, claimId, "message-7");
    }

    @Test
    void legacyFailedPushIsNotReactivatedOrSent() {
        UUID id = UUID.randomUUID();
        NotificationService notifications = mock(NotificationService.class);
        PushService pushService = mock(PushService.class);
        when(notifications.getById(id)).thenReturn(notification(id, NotificationStatus.FAILED));
        when(notifications.claimForDelivery(id, NotificationChannel.PUSH)).thenReturn(Optional.empty());

        consumer(notifications, pushService).handlePushTask(task(id));

        verify(notifications).claimForDelivery(id, NotificationChannel.PUSH);
        verifyNoInteractions(pushService);
    }

    @Test
    void aTransientCompletionFailureEscapesWithoutBecomingAProviderFailure() {
        UUID id = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        NotificationService notifications = mock(NotificationService.class);
        PushService pushService = mock(PushService.class);
        when(notifications.getById(id)).thenReturn(notification(id, NotificationStatus.QUEUED));
        when(notifications.claimForDelivery(id, NotificationChannel.PUSH)).thenReturn(Optional.of(claimId));
        when(pushService.sendPush("push-token", "Baslik", "Gövde", Map.of("jobId", "job-7"),
                Duration.ofSeconds(45)))
                .thenReturn("message-7");
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(notifications).completeDelivery(id, NotificationChannel.PUSH, claimId, "message-7");

        assertThrows(DataAccessResourceFailureException.class,
                () -> consumer(notifications, pushService).handlePushTask(task(id)));

        verify(notifications, never()).failDelivery(any(), any(), any(), any());
    }

    private Notification notification(UUID id, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setChannel(NotificationChannel.PUSH);
        notification.setStatus(status);
        return notification;
    }

    private PushTaskDto task(UUID id) {
        return new PushTaskDto(id, "push-token", "Baslik", "Gövde", Map.of("jobId", "job-7"));
    }

    private PushConsumer consumer(NotificationService notifications, PushService pushService) {
        return new PushConsumer(notifications, pushService, new NotificationDispatchProperties());
    }
}
