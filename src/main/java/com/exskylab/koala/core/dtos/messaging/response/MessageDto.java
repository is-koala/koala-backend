package com.exskylab.koala.core.dtos.messaging.response;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(UUID id, UUID senderId, String body, Instant sentAt, Instant readAt) {
}
