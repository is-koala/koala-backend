package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.session.response.LoginHistoryEntryDto;
import com.exskylab.koala.core.dtos.session.response.SessionDetailDto;
import com.exskylab.koala.core.dtos.session.response.SessionDto;
import com.exskylab.koala.entities.LoginAttempt;
import com.exskylab.koala.entities.Session;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SessionMapper {

    @Mapping(target = "deviceId", source = "session.device.id")
    @Mapping(target = "deviceType", source = "session.device.type")
    @Mapping(target = "deviceName", expression = "java(session.getDevice().displayName())")
    @Mapping(target = "current", source = "current")
    SessionDto toSessionDto(Session session, boolean current);

    @Mapping(target = "id", source = "session.id")
    @Mapping(target = "deviceId", source = "session.device.id")
    @Mapping(target = "deviceType", source = "session.device.type")
    @Mapping(target = "deviceName", expression = "java(session.getDevice().displayName())")
    @Mapping(target = "deviceCustomName", source = "session.device.customName")
    @Mapping(target = "createdAt", source = "session.createdAt")
    @Mapping(target = "lastAccessedAt", source = "session.lastAccessedAt")
    @Mapping(target = "refreshExpiresAt", source = "session.refreshExpiresAt")
    @Mapping(target = "ipAddress", source = "session.ipAddress")
    @Mapping(target = "location", source = "session.location")
    @Mapping(target = "current", source = "current")
    @Mapping(target = "logins", source = "logins")
    @Mapping(target = "loginHistory", source = "loginHistory")
    SessionDetailDto toSessionDetailDto(Session session, boolean current, long logins,
                                        List<LoginAttempt> loginHistory);

    @Mapping(target = "at", source = "timeStamp")
    @Mapping(target = "success", source = "success")
    LoginHistoryEntryDto toLoginHistoryEntryDto(LoginAttempt attempt);
}
