package com.exskylab.koala.core.dtos.messaging.response;

import com.exskylab.koala.entities.ConversationParticipantRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ConversationParticipantDto(
        @Schema(description = "Katılımcının kullanıcı kimliği; MessageDto.senderId ile eşleşir.")
        UUID id,
        String firstName,
        String lastName,
        @Schema(types = {"string", "null"})
        String profilePictureUrl,
        @Schema(description = "İlan sahibi, şirket yetkilisi ya da ödemesi emanete alınmış çalışan.")
        ConversationParticipantRole role,
        Instant joinedAt,
        @Schema(types = {"string", "null"}, format = "date-time",
                description = "Katılımcının POST /conversations/{id}/read ile en son okuduğu an. "
                        + "İstemci 'görüldü'yü lastReadAt >= sentAt ile hesaplar. Hiç okumadıysa null.")
        Instant lastReadAt,
        @Schema(description = "Katılımcı sohbetten ayrıldıysa true; eski mesajlarının adı çözülsün diye listede kalır.")
        boolean left) {
}
