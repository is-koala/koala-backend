package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.PermissionSensitivity;

import java.util.UUID;

public record StaffAuditRecord(UUID actorStaffId,
                               String actorDisplayName,
                               String permission,
                               String action,
                               String resourceType,
                               String resourceId,
                               boolean allowed,
                               boolean mutating,
                               PermissionSensitivity sensitivity,
                               String ip,
                               String userAgent) {
}
