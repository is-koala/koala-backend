package com.exskylab.koala.core.dtos.auth.request;

import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.constants.UserMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = UserMessages.NEW_PASSWORD_NOT_BLANK)
    @Size(min = 8, message = UserMessages.NEW_PASSWORD_MIN_LENGTH)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "{user.password.strong}")
    private String newPassword;
}
