package com.exskylab.koala.core.dtos.user.response;

import com.exskylab.koala.entities.DeviceType;

import java.time.Instant;

public record AdminSessionSummaryDto(long activeSessionCount, Instant lastLoginAt, DeviceType lastDevicePlatform) {
}
