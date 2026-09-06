package com.exskylab.koala.core.security;

import java.time.Instant;

public record RealtimeCredential(String token, Instant expiresAt) {
}
