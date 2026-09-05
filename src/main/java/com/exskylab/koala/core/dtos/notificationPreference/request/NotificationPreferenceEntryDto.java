package com.exskylab.koala.core.dtos.notificationPreference.request;

import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationPreferenceEntryDto {

    @NotNull
    private NotificationCategory category;

    @NotNull
    private NotificationChannel channel;

    @NotNull
    private Boolean enabled;
}
