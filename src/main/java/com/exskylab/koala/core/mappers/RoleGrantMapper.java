package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.user.response.RoleGrantDto;
import com.exskylab.koala.dataAccess.UserDao;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoleGrantMapper {

    RoleGrantDto toRoleGrantDto(UserDao.RoleGrantView grant);

    List<RoleGrantDto> toRoleGrantDtoList(List<UserDao.RoleGrantView> grants);
}
