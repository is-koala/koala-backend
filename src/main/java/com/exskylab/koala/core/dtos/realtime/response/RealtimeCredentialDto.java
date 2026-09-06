package com.exskylab.koala.core.dtos.realtime.response;

import java.time.Instant;

public record RealtimeCredentialDto(String token, Instant expiresAt) {
}
