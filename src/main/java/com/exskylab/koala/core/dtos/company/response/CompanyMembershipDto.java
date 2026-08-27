package com.exskylab.koala.core.dtos.company.response;

import com.exskylab.koala.entities.CompanyContactRole;

import java.util.UUID;

public record CompanyMembershipDto(UUID companyId, String companyName, CompanyContactRole role, boolean approved) {
}
