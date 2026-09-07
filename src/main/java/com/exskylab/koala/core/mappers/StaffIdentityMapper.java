package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.staff.response.StaffIdentityDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.StaffPermission;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StaffIdentityMapper {

    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "permissions", source = "permissions")
    @Mapping(target = "mustChangePassword", source = "mustChangePassword")
    StaffIdentityDto toIdentityDto(StaffPrincipal principal);

    default List<String> toSortedRoleNames(Collection<String> roles) {
        return roles == null ? List.of() : roles.stream().sorted().toList();
    }

    default List<String> toSortedPermissionCodes(Collection<StaffPermission> permissions) {
        return permissions == null
                ? List.of()
                : permissions.stream()
                .map(StaffPermission::getCode)
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
