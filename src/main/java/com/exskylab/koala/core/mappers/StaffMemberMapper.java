package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.staff.response.StaffMemberDto;
import com.exskylab.koala.core.security.StaffMemberView;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StaffMemberMapper {

    @Mapping(target = "id", source = "member.id")
    @Mapping(target = "email", source = "member.email")
    @Mapping(target = "displayName", source = "member.displayName")
    @Mapping(target = "active", source = "member.active")
    @Mapping(target = "mustChangePassword", source = "member.mustChangePassword")
    @Mapping(target = "lockedUntil", source = "member.lockedUntil")
    @Mapping(target = "createdAt", source = "member.createdAt")
    @Mapping(target = "roles", source = "roles")
    StaffMemberDto toMemberDto(StaffMemberView view);
}
