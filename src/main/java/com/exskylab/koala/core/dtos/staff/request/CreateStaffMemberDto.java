package com.exskylab.koala.core.dtos.staff.request;

import com.exskylab.koala.core.constants.StaffMessages;
import jakarta.validation.constraints.Email;
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
public class CreateStaffMemberDto {

    @NotBlank(message = StaffMessages.EMAIL_REQUIRED)
    @Email(message = StaffMessages.EMAIL_INVALID)
    @Size(max = 255)
    private String email;

    @Size(max = 160)
    private String displayName;

    @NotBlank(message = StaffMessages.PASSWORD_REQUIRED)
    @Size(min = 12, max = 128, message = StaffMessages.PASSWORD_TOO_SHORT)
    private String password;
}
