package com.exskylab.koala.core.dtos.staff.request;

import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.entities.StaffPermission;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GrantStaffPermissionDto {

    @NotBlank(message = StaffMessages.PERMISSION_REQUIRED)
    private String permission;

    @AssertTrue(message = StaffMessages.PERMISSION_UNKNOWN)
    public boolean isPermissionKnown() {
        return permission == null || permission.isBlank() || StaffPermission.fromCode(permission).isPresent();
    }
}
