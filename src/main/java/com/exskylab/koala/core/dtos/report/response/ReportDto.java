package com.exskylab.koala.core.dtos.report.response;

import com.exskylab.koala.entities.ReportReason;
import com.exskylab.koala.entities.ReportStatus;

import java.time.Instant;
import java.util.UUID;

public record ReportDto(UUID id, UUID reportedUserId, UUID jobId, UUID messageId, UUID imageId,
                        ReportReason reason, String description, ReportStatus status,
                        String reviewNote, Instant reviewedAt, Instant createdAt) {
}
