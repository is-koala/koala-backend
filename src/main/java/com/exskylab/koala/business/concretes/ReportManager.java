package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.ReportMessages;
import com.exskylab.koala.core.dtos.report.response.AdminReportDetailDto;
import com.exskylab.koala.core.dtos.report.response.AdminReportDto;
import com.exskylab.koala.core.mappers.ReportMapper;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.ReportDao;
import com.exskylab.koala.dataAccess.ReportedUserTally;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.security.StaffPrincipal;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportManager implements ReportService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "status");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");


    private final PageableSanitizer pageableSanitizer;
    private final ReportDao reportDao;
    private final UserService userService;
    private final JobService jobService;
    private final MessagingService messagingService;
    private final SecurityService securityService;
    private final ImageService imageService;
    private final ReportMapper reportMapper;

    public ReportManager(ReportDao reportDao, UserService userService, JobService jobService,
                         MessagingService messagingService, SecurityService securityService,
                         ImageService imageService, ReportMapper reportMapper,
                                PageableSanitizer pageableSanitizer) {
        this.reportDao = reportDao;
        this.userService = userService;
        this.jobService = jobService;
        this.messagingService = messagingService;
        this.securityService = securityService;
        this.imageService = imageService;
        this.reportMapper = reportMapper;
            this.pageableSanitizer = pageableSanitizer;
    }

    @Override
    @Transactional
    public Report createReport(UUID reportedUserId, UUID jobId, UUID messageId, UUID imageId,
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
        if (imageId != null) {
            report.setImage(imageService.getById(imageId));
        }

        Report saved = reportDao.save(report);
        log.info("Report {} created by user {} reason {}", saved.getId(), reporterId, reason);
        return saved;
    }

    @Override
    @Transactional
    public AdminReportDto review(StaffPrincipal actor, UUID reportId, ReportStatus status, String note) {
        Report report = reportDao.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND));

        if (report.getStatus() == ReportStatus.ACTIONED || report.getStatus() == ReportStatus.DISMISSED) {
            throw new ConflictException(ReportMessages.ALREADY_REVIEWED);
        }

        report.setStatus(status);
        report.setReviewNote(note);
        if (actor == null) {
            report.setReviewedBy(securityService.getAuthenticatedUser());
        }
        report.setReviewedAt(Instant.now());

        Report saved = reportDao.save(report);
        log.info("Report {} reviewed as {}", reportId, status);

        long history = saved.getReportedUser() == null ? 0L
                : reportDao.countByReportedUserId(saved.getReportedUser().getId());

        return reportMapper.toAdminReportDto(saved, history);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Report> getMyReports(Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);
        return reportDao.findAllByReporterId(securityService.getAuthenticatedUserId(),
                pageableSanitizer.sanitize(requested, SORTABLE, DEFAULT_SORT));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReportDto> searchForModeration(ReportStatus status, ReportReason reason,
                                                    ReportTargetType targetType, UUID reportedUserId,
                                                    Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);

        Page<Report> page = reportDao.search(status, reason,
                targetType == null ? null : targetType.name(), reportedUserId,
                pageableSanitizer.sanitize(requested, SORTABLE, DEFAULT_SORT));

        Map<UUID, Long> history = historyCounts(page.getContent());

        return page.map(report -> reportMapper.toAdminReportDto(report,
                report.getReportedUser() == null ? 0L
                        : history.getOrDefault(report.getReportedUser().getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportDetailDto getForModeration(UUID reportId) {
        Report report = reportDao.findDetailedById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND));

        long history = report.getReportedUser() == null ? 0L
                : reportDao.countByReportedUserId(report.getReportedUser().getId());

        log.debug("Retrieving report {} for moderation", reportId);

        return new AdminReportDetailDto(reportMapper.toAdminReportDto(report, history),
                reportMapper.toReportContentDto(report));
    }

    private Map<UUID, Long> historyCounts(List<Report> reports) {
        Set<UUID> reportedUserIds = reports.stream()
                .map(Report::getReportedUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .collect(Collectors.toSet());

        if (reportedUserIds.isEmpty()) {
            return Map.of();
        }

        return reportDao.tallyByReportedUser(reportedUserIds).stream()
                .collect(Collectors.toMap(ReportedUserTally::getUserId, ReportedUserTally::getTotal));
    }
}
