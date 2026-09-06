package com.exskylab.koala.core.dtos.staff.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffRoleRefDto {

    private UUID id;

    private String code;

    private String name;
}
