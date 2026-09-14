package com.exskylab.koala.core.dtos.report.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.ReportReason;
import com.exskylab.koala.entities.ReportStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminReportDto(UUID id, SafeUserDto reporter, SafeUserDto reportedUser, UUID reportedUserId,
                             UUID jobId, UUID jobAssignmentId, UUID messageId, UUID imageId,
                             ReportReason reason, String description, ReportStatus status,
                             SafeUserDto reviewedBy, UUID reviewedByStaffId, String reviewNote, Instant reviewedAt,
                             Instant createdAt, long reportedUserReportCount) {
}
