package com.exskylab.koala.core.dtos.address.response;

import java.time.Instant;
import java.util.UUID;

public record AddressSearchSessionDto(UUID id, Instant expiresAt) {
}
