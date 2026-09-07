package com.exskylab.koala.core.dtos.user.request;

import com.exskylab.koala.core.customValidations.ValidPassword;
import com.exskylab.koala.core.constants.UserMessages;
import jakarta.validation.constraints.NotBlank;
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

    @ValidPassword
    private String newPassword;

    @ValidPassword
    private String confirmNewPassword;
}
