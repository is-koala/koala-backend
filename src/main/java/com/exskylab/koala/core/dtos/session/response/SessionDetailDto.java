package com.exskylab.koala.core.dtos.session.response;

import com.exskylab.koala.entities.DeviceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionDetailDto(UUID id, UUID deviceId, DeviceType deviceType, String deviceName,
                               String deviceCustomName, Instant createdAt, Instant lastAccessedAt,
                               Instant refreshExpiresAt, String ipAddress, String location, boolean current,
                               long logins, List<LoginHistoryEntryDto> loginHistory) {
}
