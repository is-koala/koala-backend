package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.GetCompanyContactInvitationDto;
import com.exskylab.koala.entities.CompanyContactInvitation;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class, CompanyMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CompanyContactInvitationMapper {

    CompanyContactInvitationDto toCompanyContactInvitationDto(CompanyContactInvitation companyContactInvitation);

    List<CompanyContactInvitationDto> toCompanyContactInvitationDtoList(
            List<CompanyContactInvitation> companyContactInvitations);

    GetCompanyContactInvitationDto toGetCompanyContactInvitationDto(
            CompanyContactInvitation companyContactInvitation);
}
