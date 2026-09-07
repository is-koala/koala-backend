package com.exskylab.koala.core.dtos.staff.request;

import com.exskylab.koala.core.constants.StaffMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffLoginDto {

    @NotBlank(message = StaffMessages.EMAIL_REQUIRED)
    @Email(message = StaffMessages.EMAIL_INVALID)
    private String email;

    @NotBlank(message = StaffMessages.PASSWORD_REQUIRED)
    private String password;
}
