package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Report;
import com.exskylab.koala.entities.ReportReason;
import com.exskylab.koala.entities.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReportService {

    Report createReport(UUID reportedUserId, UUID jobId, UUID messageId, UUID imageId,
                        ReportReason reason, String description);

    Report review(UUID reportId, ReportStatus status, String note);

    Page<Report> getMyReports(Pageable pageable);

    Page<Report> getByStatus(ReportStatus status, Pageable pageable);
}
