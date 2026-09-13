package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.notificationPreference.response.UnsubscribedChannelDto;
import com.exskylab.koala.entities.UnsubscribeToken;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface NotificationPreferenceMapper {

    @Mapping(target = "category", source = "token.category")
    @Mapping(target = "channel", source = "token.channel")
    @Mapping(target = "alreadyDisabled", source = "alreadyDisabled")
    UnsubscribedChannelDto toUnsubscribedChannelDto(UnsubscribeToken token, boolean alreadyDisabled);
}
