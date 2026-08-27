package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.company.response.CompanyMembershipDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.core.dtos.user.response.UserMeResponseDto;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.User;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {MediaUrlResolver.class, AddressMapper.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UserMapper {

    @Mapping(target = "profilePictureUrl", source = "profilePicture", qualifiedByName = "imageToPublicUrl")
    SafeUserDto toSafeUserDto(User user);

    @Mapping(target = "profilePictureUrl", source = "profilePicture", qualifiedByName = "imageToPublicUrl")
    @Mapping(target = "employer", source = "employer")
    @Mapping(target = "roles", source = "authorities")
    @Mapping(target = "companies", source = "companyContacts")
    UserMeResponseDto toUserMeResponseDto(User user);

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "approved", source = "company.approved")
    CompanyMembershipDto toCompanyMembershipDto(CompanyContact contact);
}
