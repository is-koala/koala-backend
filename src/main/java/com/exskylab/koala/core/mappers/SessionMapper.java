package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.session.response.SessionDto;
import com.exskylab.koala.entities.Session;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SessionMapper {

    @Mapping(target = "deviceId", source = "session.device.id")
    @Mapping(target = "deviceType", source = "session.device.type")
    @Mapping(target = "deviceName", source = "session.device.name")
    @Mapping(target = "current", source = "current")
    SessionDto toSessionDto(Session session, boolean current);
}
