package com.exskylab.koala.core.dtos.messaging.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;

import java.time.Instant;
import java.util.UUID;

public record ConversationDto(UUID id, UUID jobId, String jobTitle,
                              SafeUserDto employer, SafeUserDto worker, Instant lastMessageAt) {
}
