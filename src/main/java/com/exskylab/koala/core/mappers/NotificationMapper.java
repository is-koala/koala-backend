package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.notification.response.NotificationDto;
import com.exskylab.koala.entities.Notification;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface NotificationMapper {

    NotificationDto toNotificationDto(Notification notification);

    List<NotificationDto> toNotificationDtoList(List<Notification> notifications);
}
