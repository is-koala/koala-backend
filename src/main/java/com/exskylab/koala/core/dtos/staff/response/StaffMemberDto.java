package com.exskylab.koala.core.dtos.staff.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffMemberDto {

    private UUID id;

    private String email;

    private String displayName;

    private boolean active;

    private boolean mustChangePassword;

    private Instant lockedUntil;

    private Instant createdAt;

    private List<String> roles;
}
