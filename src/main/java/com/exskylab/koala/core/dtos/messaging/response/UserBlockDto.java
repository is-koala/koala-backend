package com.exskylab.koala.core.dtos.messaging.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;

import java.time.Instant;
import java.util.UUID;

public record UserBlockDto(UUID id, SafeUserDto blocked, String reason, Instant createdAt) {
}
