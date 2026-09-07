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
public class StaffIdentityDto {

    private UUID staffId;

    private String displayName;

    private String email;

    private List<String> roles;

    private List<String> permissions;

    private boolean mustChangePassword;
}
