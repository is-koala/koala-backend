package com.exskylab.koala.core.dtos.staff.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffRoleDetailDto {

    private UUID id;

    private String code;

    private String name;

    private List<String> permissions;

    private List<StaffRoleRefDto> includes;

    private List<StaffRoleRefDto> effectiveRoles;

    private List<String> effectivePermissions;
}
