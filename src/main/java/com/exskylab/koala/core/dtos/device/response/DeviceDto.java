package com.exskylab.koala.core.dtos.device.response;

import com.exskylab.koala.entities.DeviceType;

import java.time.Instant;
import java.util.UUID;

public record DeviceDto(UUID id, DeviceType type, String name, String customName, Instant firstSeenAt,
                        Instant lastSeenAt) {
}
