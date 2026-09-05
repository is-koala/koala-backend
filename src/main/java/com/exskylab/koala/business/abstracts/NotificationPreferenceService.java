package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.notificationPreference.request.UpdateNotificationPreferencesDto;
import com.exskylab.koala.core.dtos.notificationPreference.response.NotificationPreferenceDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;

import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceService {

    boolean isAllowed(UUID recipientId, NotificationCategory category, NotificationChannel channel);

    List<NotificationPreferenceDto> getMyPreferences();

    List<NotificationPreferenceDto> updateMyPreferences(UpdateNotificationPreferencesDto request, ClientContext client);

    void unsubscribe(String rawToken, ClientContext client);
}
