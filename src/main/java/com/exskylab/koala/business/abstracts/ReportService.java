package com.exskylab.koala.business.abstracts;

import java.util.Optional;
import com.exskylab.koala.core.security.AuditSubject;
import com.exskylab.koala.core.dtos.report.response.AdminReportDetailDto;
import com.exskylab.koala.core.dtos.report.response.AdminReportDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.Report;
import com.exskylab.koala.entities.ReportReason;
import com.exskylab.koala.entities.ReportStatus;
import com.exskylab.koala.entities.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import com.exskylab.koala.core.helpers.CreatedAtRange;
import java.util.List;
import java.util.UUID;

public interface ReportService {

    Report createReport(UUID reportedUserId, UUID jobId, UUID messageId, UUID imageId,
                        ReportReason reason, String description);

    AdminReportDto review(StaffPrincipal actor, UUID reportId, ReportStatus status, String note);

    Page<Report> getMyReports(Pageable pageable);

    Page<AdminReportDto> searchForModeration(ReportStatus status, ReportReason reason,
                                             ReportTargetType targetType, UUID reportedUserId, UUID reporterId,
                                             String query, CreatedAtRange createdAt, Pageable pageable);

    AdminReportDetailDto getForModeration(UUID reportId);

    List<Report> findAllBetweenAssignmentParties(UUID jobId, Collection<UUID> partyIds);

    long countReceivedBy(UUID userId);

    Optional<AuditSubject> findReportedParty(UUID reportId);

    long countMadeBy(UUID userId);
}
