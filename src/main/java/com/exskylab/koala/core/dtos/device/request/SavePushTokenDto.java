package com.exskylab.koala.core.dtos.device.request;

import com.exskylab.koala.core.constants.DeviceMessages;
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
public class SavePushTokenDto {

    @NotBlank(message = DeviceMessages.PUSH_TOKEN_NOT_BLANK)
    @Size(max = 512, message = DeviceMessages.PUSH_TOKEN_TOO_LONG)
    private String pushToken;
}
