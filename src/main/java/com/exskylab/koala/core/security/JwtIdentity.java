package com.exskylab.koala.core.security;

import java.util.UUID;

public record JwtIdentity(UUID userId, UUID sessionId) {
}
