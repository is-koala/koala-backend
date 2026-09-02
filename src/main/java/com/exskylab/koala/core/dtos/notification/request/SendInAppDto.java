package com.exskylab.koala.core.dtos.notification.request;

import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationTargetType;
import com.exskylab.koala.entities.User;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SendInAppDto {

    private User recipient;

    private NotificationCategory category;

    private String subject;

    private String body;

    private NotificationTargetType targetType;

    private UUID targetId;
}
