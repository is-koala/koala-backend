package com.exskylab.koala.core.dtos.notification.response;

import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import com.exskylab.koala.entities.NotificationTargetType;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(UUID id, NotificationChannel channel, NotificationCategory category,
                              String subject, String body,
                              NotificationTargetType targetType, UUID targetId,
                              NotificationStatus status,
                              Instant sentAt, Instant readAt) {
}
