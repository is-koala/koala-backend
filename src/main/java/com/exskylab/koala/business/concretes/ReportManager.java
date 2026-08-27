package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.ReportMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.dataAccess.MessageDao;
import com.exskylab.koala.dataAccess.ReportDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReportManager implements ReportService {

    private final Logger logger = LoggerFactory.getLogger(ReportManager.class);

    private final ReportDao reportDao;
    private final UserDao userDao;
    private final JobDao jobDao;
    private final MessageDao messageDao;
    private final SecurityService securityService;

    public ReportManager(ReportDao reportDao, UserDao userDao, JobDao jobDao,
                         MessageDao messageDao, SecurityService securityService) {
        this.reportDao = reportDao;
        this.userDao = userDao;
        this.jobDao = jobDao;
        this.messageDao = messageDao;
        this.securityService = securityService;
    }

    @Override
    @Transactional
    public Report createReport(UUID reportedUserId, UUID jobId, UUID messageId,
                               ReportReason reason, String description) {

        if (reportedUserId == null && jobId == null && messageId == null) {
            throw new IllegalArgumentException(ReportMessages.TARGET_REQUIRED);
        }

        UUID reporterId = securityService.getAuthenticatedUserId();
        if (reporterId.equals(reportedUserId)) {
            throw new IllegalArgumentException(ReportMessages.CANNOT_REPORT_SELF);
        }

        Report report = new Report();
        report.setReporter(userDao.getReferenceById(reporterId));
        report.setReason(reason);
        report.setDescription(description);
        report.setStatus(ReportStatus.OPEN);

        if (reportedUserId != null) {
            report.setReportedUser(userDao.findById(reportedUserId)
                    .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND)));
        }
        if (jobId != null) {
            report.setJob(jobDao.findById(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND)));
        }
        if (messageId != null) {
            report.setMessage(messageDao.findById(messageId)
                    .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND)));
        }

        Report saved = reportDao.save(report);
        logger.info("Report {} created by user {} reason {}", saved.getId(), reporterId, reason);
        return saved;
    }

    @Override
    @Transactional
    public Report review(UUID reportId, ReportStatus status, String note) {
        Report report = reportDao.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND));

        if (report.getStatus() == ReportStatus.ACTIONED || report.getStatus() == ReportStatus.DISMISSED) {
            throw new IllegalStateException(ReportMessages.ALREADY_REVIEWED);
        }

        report.setStatus(status);
        report.setReviewNote(note);
        report.setReviewedBy(securityService.getAuthenticatedUser());
        report.setReviewedAt(Instant.now());

        Report saved = reportDao.save(report);
        logger.info("Report {} reviewed as {}", reportId, status);
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
