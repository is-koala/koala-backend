package com.exskylab.koala.core.dtos.staff.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffTokenDto {

    private String accessToken;

    private String refreshToken;

    private Instant accessExpiresAt;

    private Instant refreshExpiresAt;

    private boolean mustChangePassword;
}
