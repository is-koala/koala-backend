package com.exskylab.koala.core.dtos.auth.request;

import com.exskylab.koala.core.customValidations.ValidPassword;
import com.exskylab.koala.core.constants.AuthMessages;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordDto {

    @NotBlank(message = AuthMessages.REGISTRATION_TOKEN_NOT_NULL)
    private String resetToken;

    @ValidPassword
    private String newPassword;
}
