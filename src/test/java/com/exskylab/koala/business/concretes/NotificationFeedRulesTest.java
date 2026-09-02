package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.notification.request.SendInAppDto;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationFeedRulesTest {

    private NotificationDao notificationDao;
    private NotificationManager manager;
    private User me;

    @BeforeEach
    void setUp() {
        notificationDao = mock(NotificationDao.class);

        me = new User();
        me.setId(UUID.randomUUID());

        SecurityService security = mock(SecurityService.class);
        when(security.getAuthenticatedUserId()).thenReturn(me.getId());
        when(security.getSystemUser()).thenReturn(me);

        manager = new NotificationManager(notificationDao, mock(NotificationProducerService.class),
                mock(DeviceService.class), new tools.jackson.databind.ObjectMapper(),
                security, new PageableSanitizer());

        when(notificationDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(notificationDao.findAllByRecipientIdAndChannelOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Page.empty());
    }

    private SendInAppDto request() {
        SendInAppDto dto = new SendInAppDto();
        dto.setRecipient(me);
        dto.setCategory(NotificationCategory.APPLICATION_UPDATES);
        dto.setSubject("Garson aranıyor");
        dto.setBody("Başvurunuz kabul edildi");
        dto.setTargetType(NotificationTargetType.JOB_ASSIGNMENT);
        dto.setTargetId(UUID.randomUUID());
        return dto;
    }

    @Test
    void anInAppRecordCarriesBothTheHeadlineAndTheSentence() {
        Notification saved = manager.sendInApp(request());

        assertEquals("Garson aranıyor", saved.getSubject());
        assertEquals("Başvurunuz kabul edildi", saved.getBody());
    }

    @Test
    void anInAppRecordCarriesWhereToGoWhenTapped() {
        SendInAppDto dto = request();

        Notification saved = manager.sendInApp(dto);

        assertEquals(NotificationTargetType.JOB_ASSIGNMENT, saved.getTargetType());
        assertEquals(dto.getTargetId(), saved.getTargetId());
    }

    @Test
    void anInAppRecordIsSentImmediatelyBecauseThereIsNothingToDeliver() {
        Notification saved = manager.sendInApp(request());

        assertEquals(NotificationChannel.IN_APP, saved.getChannel());
        assertEquals(NotificationStatus.SENT, saved.getStatus());
        assertNotNull(saved.getSentAt());
    }

    @Test
    void theFeedOnlyReturnsTheInAppChannel() {
        Pageable page = PageRequest.of(0, 20);

        manager.getMyNotifications(page);

        verify(notificationDao).findAllByRecipientIdAndChannelOrderByCreatedAtDesc(
                eq(me.getId()), eq(NotificationChannel.IN_APP), any());
        verify(notificationDao, never()).findAllByRecipientIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void theUnreadBadgeCountsTheInAppChannelOnly() {
        manager.myUnreadCount();

        verify(notificationDao).countByRecipientIdAndChannelAndReadAtIsNull(
                me.getId(), NotificationChannel.IN_APP);
        verify(notificationDao, never()).countByRecipientIdAndReadAtIsNull(any());
    }

    @Test
    void markingAllReadOnlyTouchesTheInAppChannel() {
        manager.markAllRead();

        verify(notificationDao).markAllRead(eq(me.getId()), eq(NotificationChannel.IN_APP), any());
    }

    @Test
    void asingleNotificationCanBeMarkedRead() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(me);
        when(notificationDao.findById(notification.getId())).thenReturn(Optional.of(notification));

        manager.markRead(notification.getId());

        assertNotNull(notification.getReadAt());
    }

    @Test
    void markingAnAlreadyReadNotificationChangesNothing() {
        Instant firstRead = Instant.now().minusSeconds(600);
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(me);
        notification.setReadAt(firstRead);
        when(notificationDao.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertDoesNotThrow(() -> manager.markRead(notification.getId()));

        assertEquals(firstRead, notification.getReadAt());
        verify(notificationDao, never()).save(any());
    }

    @Test
    void someoneElsesNotificationIsNotMarkedRead() {
        User stranger = new User();
        stranger.setId(UUID.randomUUID());

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(stranger);
        when(notificationDao.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertDoesNotThrow(() -> manager.markRead(notification.getId()));

        assertNull(notification.getReadAt());
        verify(notificationDao, never()).save(any());
    }
}
