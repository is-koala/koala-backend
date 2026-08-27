package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.company.response.CompanyMembershipDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.core.dtos.user.response.UserMeResponseDto;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.User;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {MediaUrlResolver.class, AddressMapper.class, EducationMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UserMapper {

    @Mapping(target = "profilePictureUrl", source = "profilePicture", qualifiedByName = "imageToPublicUrl")
    SafeUserDto toSafeUserDto(User user);

    @Mapping(target = "profilePictureUrl", source = "user.profilePicture", qualifiedByName = "imageToPublicUrl")
    @Mapping(target = "employer", source = "user.employer")
    @Mapping(target = "roles", source = "user.authorities")
    @Mapping(target = "companies", source = "user.companyContacts")
    @Mapping(target = "cvUrl", source = "user.cv", qualifiedByName = "mediaToPublicUrl")
    @Mapping(target = "maskedIban", source = "maskedIban")
    @Mapping(target = "badges", source = "badges")
    UserMeResponseDto toUserMeResponseDto(User user, String maskedIban, java.util.List<com.exskylab.koala.core.dtos.badge.response.BadgeDto> badges);

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "approved", source = "company.approved")
    CompanyMembershipDto toCompanyMembershipDto(CompanyContact contact);
}
