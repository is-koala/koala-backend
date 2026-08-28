package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.ReportMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.ReportDao;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class ReportManager implements ReportService {

    private final ReportDao reportDao;
    private final UserService userService;
    private final JobService jobService;
    private final MessagingService messagingService;
    private final SecurityService securityService;

    public ReportManager(ReportDao reportDao, UserService userService, JobService jobService,
                         MessagingService messagingService, SecurityService securityService) {
        this.reportDao = reportDao;
        this.userService = userService;
        this.jobService = jobService;
        this.messagingService = messagingService;
        this.securityService = securityService;
    }

    @Override
    @Transactional
    public Report createReport(UUID reportedUserId, UUID jobId, UUID messageId,
                               ReportReason reason, String description) {

        UUID reporterId = securityService.getAuthenticatedUserId();
        if (reporterId.equals(reportedUserId)) {
            throw new ValidationException(ReportMessages.CANNOT_REPORT_SELF);
        }

        Report report = new Report();
        report.setReporter(userService.getReference(reporterId));
        report.setReason(reason);
        report.setDescription(description);
        report.setStatus(ReportStatus.OPEN);

        if (reportedUserId != null) {
            report.setReportedUser(userService.getById(reportedUserId));
        }
        if (jobId != null) {
            report.setJob(jobService.getById(jobId));
        }
        if (messageId != null) {
            report.setMessage(messagingService.getMessageById(messageId));
        }

        Report saved = reportDao.save(report);
        log.info("Report {} created by user {} reason {}", saved.getId(), reporterId, reason);
        return saved;
    }

    @Override
    @Transactional
    public Report review(UUID reportId, ReportStatus status, String note) {
        Report report = reportDao.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND));

        if (report.getStatus() == ReportStatus.ACTIONED || report.getStatus() == ReportStatus.DISMISSED) {
            throw new ConflictException(ReportMessages.ALREADY_REVIEWED);
        }

        report.setStatus(status);
        report.setReviewNote(note);
        report.setReviewedBy(securityService.getAuthenticatedUser());
        report.setReviewedAt(Instant.now());

        Report saved = reportDao.save(report);
        log.info("Report {} reviewed as {}", reportId, status);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Report> getMyReports(Pageable pageable) {
        return reportDao.findAllByReporterId(securityService.getAuthenticatedUserId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Report> getByStatus(ReportStatus status, Pageable pageable) {
        return reportDao.findAllByStatus(status, pageable);
    }
}
