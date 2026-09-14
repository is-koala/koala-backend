package com.exskylab.koala.core.dtos.messaging.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID senderId,
        @Schema(types = {"string", "null"},
                description = "Gönderenin adı soyadı; hesabını silmiş gönderende anonimleştirilmiş ad döner.")
        String senderName,
        @Schema(types = {"string", "null"})
        String senderProfilePictureUrl,
        String body,
        Instant sentAt,
        @Schema(types = {"string", "null"}, format = "date-time",
                description = "İkili sohbette karşı tarafın okuduğu an. Grupta kişi bazlı 'görüldü' için "
                        + "ConversationDto.participants[].lastReadAt kullanılır.")
        Instant readAt) {
}
