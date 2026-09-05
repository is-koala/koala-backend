package com.exskylab.koala.core.dtos.user.response;

import com.exskylab.koala.entities.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RoleGrantDto {

    private Role role;

    private Instant grantedAt;

    private UUID grantedById;

    private String grantedByName;
}
