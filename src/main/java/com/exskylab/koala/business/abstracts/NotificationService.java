package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.notification.request.*;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationChannel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.Optional;

public interface NotificationService {

    Notification sendEmail(SendEmailDto sendEmailDto, DispatchPriority dispatchPriority, boolean isSystemMail);

    Notification sendSms(SendSmsDto sendSmsDto, DispatchPriority dispatchPriority, boolean isSystemSms);

    void sendPushToUser(SendPushToUserDto sendPushToUserDto, DispatchPriority dispatchPriority, boolean isSystemPush);

    Notification sendInApp(SendInAppDto sendInAppDto);

    void markRead(UUID notificationId);



    Notification getById(UUID id);

    Notification save(Notification notification);

    Optional<UUID> claimForDelivery(UUID notificationId, NotificationChannel channel);

    void completeDelivery(UUID notificationId, NotificationChannel channel, UUID claimId, String externalId);

    void failDelivery(UUID notificationId, NotificationChannel channel, UUID claimId, String reason);

    void suppressDelivery(UUID notificationId, NotificationChannel channel, UUID claimId, String reason);

    void releaseDeliveryForRetry(UUID notificationId, NotificationChannel channel, UUID claimId, String reason);

    void terminalizeDelivery(UUID notificationId, NotificationChannel channel, String reason);

    void setAsOpened(UUID id);

    Page<Notification> getMyNotifications(Pageable pageable);

    long myUnreadCount();

    int markAllRead();
}
