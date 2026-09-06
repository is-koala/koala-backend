package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.GetCompanyContactInvitationDto;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContactInvitation;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.User;

import java.util.List;
import java.util.UUID;

public interface CompanyContactInvitationService {
    CompanyContactInvitation inviteUserToCompany(User invitedBy, User userToInvite, Company company, CompanyContactRole role);

    List<CompanyContactInvitation> getInvitationsByCompanyId(UUID companyId);

    boolean hasPendingInvitation(UUID companyId, UUID invitedUserId);

    GetCompanyContactInvitationDto answerToInvitation(UUID invitationId, boolean accepted);

    GetCompanyContactInvitationDto getInvitationById(UUID invitationId);

    List<CompanyContactInvitationDto> getCompanyContactInvitationsByCompanyIdDto(UUID companyId);
}
