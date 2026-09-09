package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.utilities.sms.SmsService;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.producers.notification.SmsTaskDto;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SmsConsumerTest {

    private NotificationService notificationService;
    private SmsService smsService;
    private SmsConsumer consumer;
    private Notification notification;
    private UUID claimId;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        smsService = mock(SmsService.class);
        consumer = new SmsConsumer(notificationService, smsService);

        notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.QUEUED);
        claimId = UUID.randomUUID();

        when(notificationService.getById(notification.getId())).thenReturn(notification);
        when(notificationService.claimForDelivery(notification.getId(), NotificationChannel.SMS))
                .thenReturn(Optional.of(claimId));
        when(notificationService.save(any())).thenAnswer(i -> i.getArgument(0));
        doAnswer(invocation -> {
            notification.setExternalId(invocation.getArgument(3));
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(java.time.Instant.now());
            notification.setFailureReason(null);
            return null;
        }).when(notificationService).completeDelivery(eq(notification.getId()), eq(NotificationChannel.SMS),
                eq(claimId), any());
        doAnswer(invocation -> {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(invocation.getArgument(3));
            return null;
        }).when(notificationService).failDelivery(eq(notification.getId()), eq(NotificationChannel.SMS),
                eq(claimId), any());
        when(smsService.sendSms(any(), any(), anyBoolean())).thenReturn("order-1");
    }

    private SmsTaskDto task(String templateName, Map<String, Object> params) {
        return new SmsTaskDto(notification.getId(), "+905551112233", templateName, params);
    }

    private SmsTaskDto rawTask(String body) {
        return task("raw-sms-template", Map.of("textContent", body));
    }

    @Test
    void aQueuedMessageIsDeliveredAndMarkedSent() {
        consumer.handleSmsTask(rawTask("Dogrulama kodunuz 123456"));

        verify(smsService).sendSms(eq(new String[]{"+905551112233"}), eq("Dogrulama kodunuz 123456"), eq(false));
        assertEquals(NotificationStatus.SENT, notification.getStatus());
        assertNotNull(notification.getSentAt());
        assertEquals("order-1", notification.getExternalId());
        verify(notificationService).completeDelivery(notification.getId(), NotificationChannel.SMS,
                claimId, "order-1");
    }

    @Test
    void aCommercialMessageCarriesTheIysFlag() {
        notification.setCategory(NotificationCategory.MARKETINGS);

        consumer.handleSmsTask(rawTask("Kampanya"));

        verify(smsService).sendSms(any(), any(), eq(true));
    }

    @Test
    void aTransactionalMessageDoesNotCarryTheIysFlag() {
        notification.setCategory(NotificationCategory.APPLICATION_UPDATES);

        consumer.handleSmsTask(rawTask("Basvurunuz kabul edildi"));

        verify(smsService).sendSms(any(), any(), eq(false));
    }

    @Test
    void aProviderFailureIsRecordedRatherThanLost() {
        when(smsService.sendSms(any(), any(), anyBoolean()))
                .thenThrow(new ExternalServiceException("saglayici hatasi"));

        assertDoesNotThrow(() -> consumer.handleSmsTask(rawTask("Kod 1")));

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertNotNull(notification.getFailureReason());
        verify(notificationService).failDelivery(eq(notification.getId()), eq(NotificationChannel.SMS),
                eq(claimId), contains("saglayici hatasi"));
    }

    @Test
    void aMissingNotificationRowIsDroppedWithoutSending() {
        UUID unknown = UUID.randomUUID();
        when(notificationService.getById(unknown)).thenThrow(new ResourceNotFoundException("yok"));

        assertDoesNotThrow(() -> consumer.handleSmsTask(
                new SmsTaskDto(unknown, "+905551112233", "raw-sms-template", Map.of("textContent", "x"))));

        verifyNoInteractions(smsService);
    }

    @Test
    void aTransientNotificationLookupFailureEscapesSoRabbitCanRetry() {
        when(notificationService.getById(notification.getId()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> consumer.handleSmsTask(rawTask("Kod 1")));

        verifyNoInteractions(smsService);
    }

    @Test
    void aTransientCompletionFailureEscapesWithoutBeingMisclassifiedAsAProviderFailure() {
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(notificationService).completeDelivery(notification.getId(), NotificationChannel.SMS,
                        claimId, "order-1");

        assertThrows(DataAccessResourceFailureException.class,
                () -> consumer.handleSmsTask(rawTask("Kod 1")));

        verify(notificationService, never()).failDelivery(any(), any(), any(), any());
    }

    @Test
    void aTaskWithNoRenderableBodyFailsInsteadOfSendingAnEmptyMessage() {
        consumer.handleSmsTask(task("raw-sms-template", Map.of()));

        verifyNoInteractions(smsService);
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        verify(notificationService).failDelivery(eq(notification.getId()), eq(NotificationChannel.SMS),
                eq(claimId), any());
    }

    @Test
    void anUnknownTemplateFailsRatherThanGuessing() {
        consumer.handleSmsTask(task("some-template", Map.of("textContent", "x")));

        verifyNoInteractions(smsService);
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        verify(notificationService).failDelivery(eq(notification.getId()), eq(NotificationChannel.SMS),
                eq(claimId), any());
    }
}
