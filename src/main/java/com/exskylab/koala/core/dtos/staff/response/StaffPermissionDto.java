package com.exskylab.koala.core.dtos.staff.response;

import com.exskylab.koala.entities.PermissionSensitivity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffPermissionDto {

    private String code;

    private boolean mutating;

    private PermissionSensitivity sensitivity;
}
