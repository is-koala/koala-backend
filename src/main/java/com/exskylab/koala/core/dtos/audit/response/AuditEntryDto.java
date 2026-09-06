package com.exskylab.koala.core.dtos.audit.response;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryDto(
        UUID id,
        Instant occurredAt,
        UUID actorStaffId,
        String actorDisplayName,
        String permission,
        String action,
        String resourceType,
        String resourceId,
        boolean allowed,
        boolean mutating,
        String sensitivity,
        String requestBody,
        String ip,
        String userAgent,
        String before,
        String after) {
}
