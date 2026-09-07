package com.exskylab.koala.core.dtos.staff.request;

import com.exskylab.koala.core.constants.StaffMessages;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateStaffMemberDto {

    @NotNull(message = StaffMessages.ACTIVE_REQUIRED)
    private Boolean active;
}
