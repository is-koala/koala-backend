package com.exskylab.koala.core.dtos.user.response;

import com.exskylab.koala.entities.CompanyContactRole;

import java.util.UUID;

public record AdminUserCompanyDto(UUID id, String name, CompanyContactRole role) {
}
