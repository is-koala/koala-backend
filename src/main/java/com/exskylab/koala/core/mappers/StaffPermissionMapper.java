package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.staff.response.StaffPermissionDto;
import com.exskylab.koala.entities.StaffPermission;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StaffPermissionMapper {

    StaffPermissionDto toPermissionDto(StaffPermission permission);

    List<StaffPermissionDto> toPermissionDtoList(List<StaffPermission> permissions);
}
