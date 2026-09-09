package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationDispatchService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.logging.LogContext;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "notification.dispatch.enabled=true",
        "notification.dispatch.fixed-delay=PT1H"
})
class NotificationOutboxAtomicityIT extends AbstractIntegrationTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationDispatchService dispatchService;
    @Autowired private NotificationDao notificationDao;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private NotificationProducerService producer;
    @MockitoBean private SecurityService securityService;
    private UUID committedNotificationId;

    @BeforeEach
    void useTheSystemAuditor() {
        when(securityService.getAuthenticatedUserId()).thenThrow(new IllegalStateException("no request principal"));
    }

    @AfterEach
    void cleanCommittedFixture() {
        LogContext.clear();
        if (committedNotificationId != null) {
            notificationDao.deleteById(committedNotificationId);
        }
    }

    @Test
    void theOriginatingRequestContextIsPersistedWithTheOutboxIntent() {
        LogContext.adopt(Map.of(
                LogContext.REQUEST_ID, "request-7",
                LogContext.USER_ID, "user-7",
                LogContext.SESSION_ID, "session-7",
                LogContext.DEVICE_ID, "device-7"));

        Notification accepted = notificationService.sendEmail(email(), DispatchPriority.NORMAL, true);
        committedNotificationId = accepted.getId();

        Notification stored = notificationDao.findById(accepted.getId()).orElseThrow();
        assertEquals("request-7", stored.getDispatchRequestId());
        assertEquals("user-7", stored.getDispatchUserId());
        assertEquals("session-7", stored.getDispatchSessionId());
        assertEquals("device-7", stored.getDispatchDeviceId());
        stored.setNextDispatchAt(Instant.EPOCH);
        notificationDao.saveAndFlush(stored);

        AtomicReference<Map<String, String>> publishedContext = new AtomicReference<>();
        doAnswer(invocation -> {
            publishedContext.set(LogContext.carried());
            return null;
        }).when(producer).sendEmailTask(any());
        LogContext.adopt(Map.of(LogContext.REQUEST_ID, "scheduler-request"));

        assertTrue(dispatchService.runTask(stored.getId()));
        assertEquals(Map.of(
                LogContext.REQUEST_ID, "request-7",
                LogContext.USER_ID, "user-7",
                LogContext.SESSION_ID, "session-7",
                LogContext.DEVICE_ID, "device-7"), publishedContext.get());
        assertEquals(Map.of(LogContext.REQUEST_ID, "scheduler-request"), LogContext.carried());
    }

    @Test
    void rollingBackTheCallerTransactionLeavesNeitherNotificationNorPublishIntent() {
        AtomicReference<UUID> notificationId = new AtomicReference<>();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            notificationId.set(notificationService.sendEmail(email(), DispatchPriority.NORMAL, true).getId());
            status.setRollbackOnly();
        });

        assertTrue(notificationDao.findById(notificationId.get()).isEmpty());
        verifyNoInteractions(producer);
    }

    @Test
    void callerSuccessMeansTheNotificationAndExactPublishIntentWereDurablyAcceptedTogether() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Notification accepted = transaction.execute(status ->
                notificationService.sendEmail(email(), DispatchPriority.HIGH, true));
        committedNotificationId = accepted.getId();

        Notification stored = notificationDao.findById(accepted.getId()).orElseThrow();
        EmailTaskDto task = objectMapper.readValue(stored.getDispatchPayload(), EmailTaskDto.class);
        assertEquals(NotificationStatus.QUEUED, stored.getStatus());
        assertEquals(DispatchPriority.HIGH, stored.getDispatchPriority());
        assertEquals(0, stored.getDispatchAttempts());
        assertNotNull(stored.getNextDispatchAt());
        assertEquals(stored.getId(), task.id());
        assertEquals("outbox@koala.test", task.to());
        assertEquals("Hesap guvenligi", task.subject());
        assertEquals("account-security-template", task.templateName());
        assertEquals("Ada", task.templateParameters().get("firstName"));
        verifyNoInteractions(producer);
    }

    private SendEmailDto email() {
        SendEmailDto email = new SendEmailDto();
        email.setDestinationEmail("outbox@koala.test");
        email.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        email.setSubject("Hesap guvenligi");
        email.setTemplateName("account-security-template");
        email.setTemplateParameters(Map.of("firstName", "Ada"));
        return email;
    }
}
