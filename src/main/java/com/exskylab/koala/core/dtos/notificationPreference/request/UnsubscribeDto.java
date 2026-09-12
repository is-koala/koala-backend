package com.exskylab.koala.core.dtos.notificationPreference.request;

import com.exskylab.koala.core.constants.NotificationPreferenceMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UnsubscribeDto {

    @NotBlank(message = NotificationPreferenceMessages.UNSUBSCRIBE_TOKEN_REQUIRED)
    @Size(max = 2048, message = NotificationPreferenceMessages.UNSUBSCRIBE_TOKEN_TOO_LONG)
    private String token;
}
