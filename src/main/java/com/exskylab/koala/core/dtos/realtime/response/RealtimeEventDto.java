package com.exskylab.koala.core.dtos.realtime.response;

import com.exskylab.koala.core.dtos.messaging.response.MessageDto;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEventDto(RealtimeEventType type, UUID conversationId, UUID userId,
                               MessageDto message, Boolean online, Instant at) {

    public static RealtimeEventDto message(UUID conversationId, MessageDto message) {
        return new RealtimeEventDto(RealtimeEventType.MESSAGE, conversationId,
                message.senderId(), message, null, Instant.now());
    }

    public static RealtimeEventDto typing(UUID conversationId, UUID userId) {
        return new RealtimeEventDto(RealtimeEventType.TYPING, conversationId, userId,
                null, null, Instant.now());
    }

    public static RealtimeEventDto read(UUID conversationId, UUID userId) {
        return new RealtimeEventDto(RealtimeEventType.READ, conversationId, userId,
                null, null, Instant.now());
    }

    public static RealtimeEventDto presence(UUID userId, boolean online) {
        return new RealtimeEventDto(RealtimeEventType.PRESENCE, null, userId,
                null, online, Instant.now());
    }
}
