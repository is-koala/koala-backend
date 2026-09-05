package com.exskylab.koala.core.dtos.user.request;

import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.entities.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GrantRoleDto {

    @NotNull(message = AdminMessages.ROLE_REQUIRED)
    private Role role;
}
