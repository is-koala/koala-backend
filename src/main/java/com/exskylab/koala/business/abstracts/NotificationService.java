package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.notification.request.*;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.UUID;

public interface NotificationService {

    Notification sendEmail(SendEmailDto sendEmailDto, DispatchPriority dispatchPriority, boolean isSystemMail);

    Notification sendSms(SendSmsDto sendSmsDto, DispatchPriority dispatchPriority, boolean isSystemSms);

    void sendPushToUser(SendPushToUserDto sendPushToUserDto, DispatchPriority dispatchPriority, boolean isSystemPush);

    void preparePushRecipients(Collection<UUID> recipientIds);

    Notification sendInApp(SendInAppDto sendInAppDto);

    void markRead(UUID notificationId);



    Notification getById(UUID id);

    Notification save(Notification notification);

    void setAsOpened(UUID id);

    Page<Notification> getMyNotifications(Pageable pageable);

    long myUnreadCount();

    int markAllRead();
}
