package com.exskylab.koala.core.dtos.notificationPreference.response;

import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationCategoryKind;
import com.exskylab.koala.entities.NotificationChannel;

public record NotificationPreferenceDto(
        NotificationCategory category,
        NotificationCategoryKind kind,
        NotificationChannel channel,
        boolean enabled,
        boolean editable) {
}
