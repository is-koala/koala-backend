package com.exskylab.koala.core.dtos.notification.response;

import com.exskylab.koala.core.dtos.common.PageDto;

public record NotificationFeedDto(PageDto<NotificationDto> notifications, long unreadCount) {
}
