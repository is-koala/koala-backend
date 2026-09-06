package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.audit.response.AuditEntryDto;
import com.exskylab.koala.entities.AdminAuditEntry;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AuditEntryMapper {

    @Mapping(target = "requestBody", source = "detail.requestBody")
    @Mapping(target = "ip", source = "detail.ip")
    @Mapping(target = "userAgent", source = "detail.userAgent")
    @Mapping(target = "before", source = "detail.beforeValue")
    @Mapping(target = "after", source = "detail.afterValue")
    AuditEntryDto toAuditEntryDto(AdminAuditEntry entry);
}
