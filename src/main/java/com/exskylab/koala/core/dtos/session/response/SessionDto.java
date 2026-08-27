package com.exskylab.koala.core.dtos.session.response;

import com.exskylab.koala.entities.DeviceType;

import java.time.Instant;
import java.util.UUID;

public record SessionDto(UUID id, UUID deviceId, DeviceType deviceType, String deviceName,
                         String ipAddress, String location, Instant lastAccessedAt,
                         Instant refreshExpiresAt, boolean current) {
}
