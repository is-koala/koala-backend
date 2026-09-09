package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationTrackingFenceIT extends AbstractIntegrationTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationDao notificationDao;
    @Autowired private JdbcTemplate jdbcTemplate;
    private UUID notificationId;

    @AfterEach
    void cleanFixture() {
        if (notificationId != null) {
            jdbcTemplate.update("delete from notifications where id = ?", notificationId);
        }
    }

    @Test
    void trackingCannotStealAnEmailDeliveryLeaseBeforeSmtpCompletionIsPersisted() {
        UUID deliveryClaimId = UUID.randomUUID();
        Instant deliveryClaimedAt = Instant.parse("2026-09-09T12:00:00Z");
        Instant publishedAt = Instant.parse("2026-09-09T11:59:00Z");
        Instant nextDispatchAt = Instant.parse("2026-09-09T11:58:00Z");
        String dispatchPayload = "{\"id\":\"exact-payload\"}";

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setDeliveryClaimId(deliveryClaimId);
        notification.setDeliveryClaimedAt(deliveryClaimedAt);
        notification.setDispatchPayload(dispatchPayload);
        notification.setDispatchPriority(DispatchPriority.HIGH);
        notification.setDispatchAttempts(2);
        notification.setNextDispatchAt(nextDispatchAt);
        notification.setPublishedAt(publishedAt);
        notification = notificationDao.saveAndFlush(notification);
        notificationId = notification.getId();

        notificationService.setAsOpened(notificationId);

        Notification stored = notificationDao.findById(notificationId).orElseThrow();
        assertEquals(NotificationStatus.PROCESSING, stored.getStatus());
        assertEquals(deliveryClaimId, stored.getDeliveryClaimId());
        assertEquals(deliveryClaimedAt, stored.getDeliveryClaimedAt());
        assertEquals(publishedAt, stored.getPublishedAt());
        assertEquals(dispatchPayload, stored.getDispatchPayload());
        assertEquals(DispatchPriority.HIGH, stored.getDispatchPriority());
        assertEquals(2, stored.getDispatchAttempts());
        assertEquals(nextDispatchAt, stored.getNextDispatchAt());
    }

    @Test
    void trackingMarksOnlyDeliveredEmailAsOpenedWithoutRewritingOutboxState() {
        Instant sentAt = Instant.parse("2026-09-09T12:00:00Z");
        Instant publishedAt = Instant.parse("2026-09-09T11:59:00Z");
        Instant nextDispatchAt = Instant.parse("2026-09-09T11:58:00Z");
        String dispatchPayload = "{\"id\":\"delivered-payload\"}";

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(sentAt);
        notification.setDispatchPayload(dispatchPayload);
        notification.setDispatchPriority(DispatchPriority.HIGH);
        notification.setDispatchAttempts(2);
        notification.setNextDispatchAt(nextDispatchAt);
        notification.setPublishedAt(publishedAt);
        notification = notificationDao.saveAndFlush(notification);
        notificationId = notification.getId();

        notificationService.setAsOpened(notificationId);

        Notification stored = notificationDao.findById(notificationId).orElseThrow();
        assertEquals(NotificationStatus.OPENED, stored.getStatus());
        assertEquals(sentAt, stored.getSentAt());
        assertEquals(publishedAt, stored.getPublishedAt());
        assertEquals(dispatchPayload, stored.getDispatchPayload());
        assertEquals(DispatchPriority.HIGH, stored.getDispatchPriority());
        assertEquals(2, stored.getDispatchAttempts());
        assertEquals(nextDispatchAt, stored.getNextDispatchAt());
    }

    @Test
    void trackingDoesNotDowngradeClickedEmailToOpened() {
        Instant sentAt = Instant.parse("2026-09-09T12:00:00Z");
        Instant publishedAt = Instant.parse("2026-09-09T11:59:00Z");

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.CLICKED);
        notification.setSentAt(sentAt);
        notification.setPublishedAt(publishedAt);
        notification = notificationDao.saveAndFlush(notification);
        notificationId = notification.getId();

        notificationService.setAsOpened(notificationId);

        Notification stored = notificationDao.findById(notificationId).orElseThrow();
        assertEquals(NotificationStatus.CLICKED, stored.getStatus());
        assertEquals(sentAt, stored.getSentAt());
        assertEquals(publishedAt, stored.getPublishedAt());
    }
}
