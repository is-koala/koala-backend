package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.staff.response.StaffRoleDetailDto;
import com.exskylab.koala.core.dtos.staff.response.StaffRoleDto;
import com.exskylab.koala.core.dtos.staff.response.StaffRoleRefDto;
import com.exskylab.koala.core.security.StaffRoleView;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.StaffRole;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StaffRoleMapper {

    @Mapping(target = "id", source = "role.id")
    @Mapping(target = "code", source = "role.code")
    @Mapping(target = "name", source = "role.name")
    StaffRoleDto toRoleDto(StaffRoleView view);

    List<StaffRoleDto> toRoleDtoList(List<StaffRoleView> views);

    @Mapping(target = "id", source = "role.id")
    @Mapping(target = "code", source = "role.code")
    @Mapping(target = "name", source = "role.name")
    StaffRoleDetailDto toRoleDetailDto(StaffRoleView view);

    StaffRoleRefDto toRoleRefDto(StaffRole role);

    List<StaffRoleRefDto> toRoleRefDtoList(List<StaffRole> roles);

    default List<String> toPermissionCodes(Collection<StaffPermission> permissions) {
        return permissions == null
                ? List.of()
                : permissions.stream().map(StaffPermission::getCode).sorted().toList();
    }
}
