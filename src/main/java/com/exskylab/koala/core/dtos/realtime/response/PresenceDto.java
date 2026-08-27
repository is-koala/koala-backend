package com.exskylab.koala.core.dtos.realtime.response;

import java.time.Instant;
import java.util.UUID;

public record PresenceDto(UUID userId, boolean online, Instant lastSeenAt) {
}
