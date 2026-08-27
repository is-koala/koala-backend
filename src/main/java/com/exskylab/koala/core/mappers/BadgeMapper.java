package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.badge.response.BadgeDto;
import com.exskylab.koala.entities.Badge;
import com.exskylab.koala.entities.UserBadge;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface BadgeMapper {

    @Mapping(target = "code", source = "badge.code")
    @Mapping(target = "family", source = "badge.family")
    @Mapping(target = "tier", source = "badge.tier")
    @Mapping(target = "name", source = "badge.name")
    @Mapping(target = "description", source = "badge.description")
    BadgeDto toBadgeDto(UserBadge userBadge);

    List<BadgeDto> toBadgeDtoList(List<UserBadge> userBadges);

    @Mapping(target = "earnedAt", ignore = true)
    BadgeDto toCatalogDto(Badge badge);

    List<BadgeDto> toCatalogDtoList(List<Badge> badges);
}
