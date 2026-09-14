package com.exskylab.koala.core.dtos.realtime.response;

import com.exskylab.koala.core.dtos.messaging.response.MessageDto;
import com.exskylab.koala.entities.RealtimeEventType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEventDto(RealtimeEventType type, UUID conversationId,
                               @Schema(description = "MESSAGE'da gönderen, TYPING'de yazan, READ'de okuyan, "
                                       + "PRESENCE'ta durumu değişen kullanıcı.")
                               UUID userId,
                               MessageDto message, Boolean online, Instant at,
                               @Schema(types = {"string", "null"}, format = "date-time",
                                       description = "Yalnız READ: okuyanın (userId) yeni lastReadAt damgası; "
                                               + "ConversationDto.participants[].lastReadAt ile aynı değer.")
                               Instant lastReadAt,
                               RealtimeErrorDto error) {

    public static RealtimeEventDto message(UUID conversationId, MessageDto message) {
        return new RealtimeEventDto(RealtimeEventType.MESSAGE, conversationId,
                message.senderId(), message, null, Instant.now(), null, null);
    }

    public static RealtimeEventDto typing(UUID conversationId, UUID userId) {
        return new RealtimeEventDto(RealtimeEventType.TYPING, conversationId, userId,
                null, null, Instant.now(), null, null);
    }

    public static RealtimeEventDto read(UUID conversationId, UUID readerId, Instant lastReadAt) {
        return new RealtimeEventDto(RealtimeEventType.READ, conversationId, readerId,
                null, null, Instant.now(), lastReadAt, null);
    }

    public static RealtimeEventDto presence(UUID userId, boolean online) {
        return new RealtimeEventDto(RealtimeEventType.PRESENCE, null, userId,
                null, online, Instant.now(), null, null);
    }

    public static RealtimeEventDto subscriptionRefused(String destination, String errorCode, String message) {
        return new RealtimeEventDto(RealtimeEventType.ERROR, null, null,
                null, null, Instant.now(), null, new RealtimeErrorDto(destination, errorCode, message));
    }
}
