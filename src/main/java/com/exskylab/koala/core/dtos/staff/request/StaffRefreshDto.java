package com.exskylab.koala.core.dtos.staff.request;

import com.exskylab.koala.core.constants.StaffMessages;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffRefreshDto {

    @NotBlank(message = StaffMessages.REFRESH_TOKEN_REQUIRED)
    private String refreshToken;
}
