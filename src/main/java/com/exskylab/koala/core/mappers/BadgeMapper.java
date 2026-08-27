package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.badge.response.BadgeAdminDto;
import com.exskylab.koala.core.dtos.badge.response.BadgeDto;
import com.exskylab.koala.entities.Badge;
import com.exskylab.koala.entities.UserBadge;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = MediaUrlResolver.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface BadgeMapper {

    @Mapping(target = "code", source = "badge.code")
    @Mapping(target = "family", source = "badge.family")
    @Mapping(target = "tier", source = "badge.tier")
    @Mapping(target = "name", source = "badge.name")
    @Mapping(target = "description", source = "badge.description")
    @Mapping(target = "iconUrl", source = "badge.icon", qualifiedByName = "imageToPublicUrl")
    BadgeDto toBadgeDto(UserBadge userBadge);

    List<BadgeDto> toBadgeDtoList(List<UserBadge> userBadges);

    @Mapping(target = "earnedAt", ignore = true)
    @Mapping(target = "iconUrl", source = "icon", qualifiedByName = "imageToPublicUrl")
    BadgeDto toCatalogDto(Badge badge);

    List<BadgeDto> toCatalogDtoList(List<Badge> badges);

    @Mapping(target = "iconUrl", source = "icon", qualifiedByName = "imageToPublicUrl")
    BadgeAdminDto toBadgeAdminDto(Badge badge);

    List<BadgeAdminDto> toBadgeAdminDtoList(List<Badge> badges);
}
