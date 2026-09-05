package com.exskylab.koala.core.dtos.notificationPreference.request;

import com.exskylab.koala.core.constants.NotificationPreferenceMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.stream.Stream;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateNotificationPreferencesDto {

    @NotEmpty
    @Size(max = 64)
    @Valid
    private List<NotificationPreferenceEntryDto> preferences;

    @AssertTrue(message = NotificationPreferenceMessages.NOT_OPTIONAL)
    public boolean isLeavingTransactionalNotificationsOn() {
        return entries().noneMatch(entry -> !entry.getCategory().getKind().isOptional()
                && Boolean.FALSE.equals(entry.getEnabled()));
    }

    @AssertTrue(message = NotificationPreferenceMessages.CHANNEL_UNSUPPORTED)
    public boolean isNamingOnlyPreferableChannels() {
        return entries().allMatch(entry -> entry.getChannel().isPreferable());
    }

    private Stream<NotificationPreferenceEntryDto> entries() {
        return preferences == null ? Stream.empty()
                : preferences.stream().filter(entry -> entry != null
                        && entry.getCategory() != null && entry.getChannel() != null);
    }
}
