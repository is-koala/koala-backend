package com.exskylab.koala.core.dtos.messaging.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.ConversationKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDto(UUID id, UUID jobId, String jobTitle, ConversationKind kind,
                              SafeUserDto employer, SafeUserDto worker, List<SafeUserDto> participants,
                              Instant lastMessageAt, String lastMessageBody, long unreadCount,
                              boolean closed) {
}
