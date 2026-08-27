package com.exskylab.koala.core.dtos.user.request;

import com.exskylab.koala.core.constants.UserMessages;
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
public class ChangePasswordDto {

    @NotBlank(message = UserMessages.CURRENT_PASSWORD_NOT_BLANK)
    private String currentPassword;

    @NotBlank(message = UserMessages.NEW_PASSWORD_NOT_BLANK)
    @Size(min = 8, message = UserMessages.NEW_PASSWORD_MIN_LENGTH)
    private String newPassword;

    @NotBlank(message = UserMessages.CONFIRM_NEW_PASSWORD_NOT_BLANK)
    @Size(min = 8, message = UserMessages.NEW_PASSWORD_MIN_LENGTH)
    private String confirmNewPassword;
}
