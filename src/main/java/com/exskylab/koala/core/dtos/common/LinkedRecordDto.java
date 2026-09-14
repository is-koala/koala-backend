package com.exskylab.koala.core.dtos.common;

import java.time.Instant;
import java.util.UUID;

public record LinkedRecordDto(UUID id, String status, Instant createdAt) {
}
