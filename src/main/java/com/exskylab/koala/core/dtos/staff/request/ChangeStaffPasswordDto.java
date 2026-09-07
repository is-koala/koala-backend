package com.exskylab.koala.core.dtos.staff.request;

import com.exskylab.koala.core.constants.StaffMessages;
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
public class ChangeStaffPasswordDto {

    @NotBlank(message = StaffMessages.PASSWORD_REQUIRED)
    private String currentPassword;

    @NotBlank(message = StaffMessages.PASSWORD_REQUIRED)
    @Size(min = 12, max = 128, message = StaffMessages.PASSWORD_TOO_SHORT)
    private String newPassword;
}
