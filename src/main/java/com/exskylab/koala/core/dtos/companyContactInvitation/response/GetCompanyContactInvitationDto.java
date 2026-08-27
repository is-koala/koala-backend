package com.exskylab.koala.core.dtos.companyContactInvitation.response;

import com.exskylab.koala.core.dtos.company.response.OnlyCompanyDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.CompanyContactInvitationStatus;
import com.exskylab.koala.entities.CompanyContactRole;
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
public class GetCompanyContactInvitationDto {

    private UUID id;

    private OnlyCompanyDto company;

    private SafeUserDto invitedBy;

    private SafeUserDto invitedUser;

    private CompanyContactRole role;

    private CompanyContactInvitationStatus status;

    private Instant invitedAt;

    private Instant answeredAt;

}
