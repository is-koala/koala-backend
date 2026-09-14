package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.StaffAccountService;
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
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.helpers.StaffSearchQuery;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
    private final StaffAccountService staffAccountService;
    private final JobAssignmentService jobAssignmentService;

    public ReportManager(ReportDao reportDao, UserService userService, JobService jobService,
                         MessagingService messagingService, SecurityService securityService,
                         ImageService imageService, ReportMapper reportMapper,
                         StaffAccountService staffAccountService, JobAssignmentService jobAssignmentService,
                         PageableSanitizer pageableSanitizer) {
        this.reportDao = reportDao;
        this.userService = userService;
        this.jobService = jobService;
        this.messagingService = messagingService;
        this.securityService = securityService;
        this.imageService = imageService;
        this.reportMapper = reportMapper;
        this.staffAccountService = staffAccountService;
        this.jobAssignmentService = jobAssignmentService;
        this.pageableSanitizer = pageableSanitizer;
    }

    @Override
    @Transactional
    public Report createReport(UUID reportedUserId, UUID jobId, UUID messageId, UUID imageId,
                               ReportReason reason, String description) {

        UUID reporterId = securityService.getAuthenticatedUserId();
        if (messageId == null && reporterId.equals(reportedUserId)) {
            throw new ValidationException(ReportMessages.CANNOT_REPORT_SELF);
        }
        Message reportedMessage = messageId == null
                ? null
                : messagingService.getMessageForReport(messageId, reporterId);
        if (reportedMessage != null && reporterId.equals(reportedMessage.getSender().getId())) {
            throw new ValidationException(ReportMessages.CANNOT_REPORT_SELF);
        }
        String storedDescription = reportedMessage == null ? description : normalizeDescription(description);
        Job reportedJob = jobId == null ? null : jobService.getById(jobId);
        Image reportedImage = imageId == null ? null : imageService.getById(imageId);
        if (reportedMessage != null) {
            MessageReportFingerprint requested = MessageReportFingerprint.from(
                    reportedMessage, reportedJob, reportedImage, reason, storedDescription);
            Report existing = reportDao.findByReporterIdAndMessageId(reporterId, messageId).orElse(null);
            if (existing != null) {
                if (MessageReportFingerprint.from(existing, reportedMessage).equals(requested)) {
                    existing.setReportedUser(reportedMessage.getSender());
                    return existing;
                }
                throw new ConflictException(ReportMessages.IDEMPOTENCY_CONFLICT);
            }
        }

        Report report = new Report();
        report.setReporter(userService.getReference(reporterId));
        report.setReason(reason);
        report.setDescription(storedDescription);
        report.setStatus(ReportStatus.OPEN);

        if (reportedMessage != null) {
            report.setReportedUser(reportedMessage.getSender());
        } else if (reportedUserId != null) {
            report.setReportedUser(userService.getById(reportedUserId));
        }
        if (reportedJob != null) {
            report.setJob(reportedJob);
        }
        if (reportedMessage != null) {
            report.setMessage(reportedMessage);
        }
        if (reportedImage != null) {
            report.setImage(reportedImage);
        }

        Report saved = reportDao.save(report);
        log.info("Report {} created by user {} reason {}", saved.getId(), reporterId, reason);
        return saved;
    }

    @Override
    @Transactional
    public AdminReportDto review(StaffPrincipal actor, UUID reportId, ReportStatus status, String note) {
        Report report = reportDao.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND));

        if (report.getStatus() == ReportStatus.ACTIONED || report.getStatus() == ReportStatus.DISMISSED) {
            throw new ConflictException(ReportMessages.ALREADY_REVIEWED);
        }

        report.setStatus(status);
        report.setReviewNote(note);
        if (actor == null) {
            report.setReviewedBy(securityService.getAuthenticatedUser());
            report.setReviewedByStaff(null);
        } else {
            report.setReviewedBy(null);
            report.setReviewedByStaff(staffAccountService.getReference(actor.staffId()));
        }
        report.setReviewedAt(Instant.now());

        Report saved = reportDao.save(report);
        log.info("Report {} reviewed as {}", reportId, status);

        long history = saved.getReportedUser() == null ? 0L
                : reportDao.countByReportedUserId(saved.getReportedUser().getId());

        return reportMapper.toAdminReportDto(saved, history, assignmentIds(List.of(saved)).get(saved.getId()));
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
                                                    UUID reporterId, String query, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);

        Page<Report> page = reportDao.search(status, reason,
                targetType == null ? null : targetType.name(), reportedUserId, reporterId,
                StaffSearchQuery.normalize(query),
                pageableSanitizer.sanitize(requested, SORTABLE, DEFAULT_SORT));

        Map<UUID, Long> history = historyCounts(page.getContent());
        Map<UUID, UUID> assignments = assignmentIds(page.getContent());

        return page.map(report -> reportMapper.toAdminReportDto(report,
                report.getReportedUser() == null ? 0L
                        : history.getOrDefault(report.getReportedUser().getId(), 0L),
                assignments.get(report.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Report> findAllBetweenAssignmentParties(UUID jobId, Collection<UUID> partyIds) {
        if (partyIds.isEmpty()) {
            return List.of();
        }
        return reportDao.findAllBetweenAssignmentParties(jobId, partyIds);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportDetailDto getForModeration(UUID reportId) {
        Report report = reportDao.findDetailedById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(ReportMessages.NOT_FOUND));

        long history = report.getReportedUser() == null ? 0L
                : reportDao.countByReportedUserId(report.getReportedUser().getId());

        log.debug("Retrieving report {} for moderation", reportId);

        return new AdminReportDetailDto(
                reportMapper.toAdminReportDto(report, history, assignmentIds(List.of(report)).get(report.getId())),
                reportMapper.toReportContentDto(report));
    }

    private Map<UUID, UUID> assignmentIds(List<Report> reports) {
        List<Report> aboutJobs = reports.stream().filter(report -> report.getJob() != null).toList();
        if (aboutJobs.isEmpty()) {
            return Map.of();
        }
        Set<UUID> jobIds = aboutJobs.stream().map(report -> report.getJob().getId()).collect(Collectors.toSet());
        Set<UUID> partyIds = new HashSet<>();
        for (Report report : aboutJobs) {
            partyIds.add(report.getReporter().getId());
            if (report.getReportedUser() != null) {
                partyIds.add(report.getReportedUser().getId());
            }
        }
        Map<Map.Entry<UUID, UUID>, UUID> byJobAndWorker = new HashMap<>();
        for (JobAssignment assignment : jobAssignmentService.findLatestByJobsAndWorkers(jobIds, partyIds)) {
            byJobAndWorker.put(Map.entry(assignment.getJob().getId(), assignment.getWorker().getId()),
                    assignment.getId());
        }

        Map<UUID, UUID> resolved = new HashMap<>();
        for (Report report : aboutJobs) {
            UUID jobId = report.getJob().getId();
            UUID assignmentId = byJobAndWorker.get(Map.entry(jobId, report.getReporter().getId()));
            if (assignmentId == null && report.getReportedUser() != null) {
                assignmentId = byJobAndWorker.get(Map.entry(jobId, report.getReportedUser().getId()));
            }
            if (assignmentId != null) {
                resolved.put(report.getId(), assignmentId);
            }
        }
        return resolved;
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

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private record MessageReportFingerprint(UUID reportedUserId, UUID jobId, UUID messageId, UUID imageId,
                                            ReportReason reason, String description) {

        private static MessageReportFingerprint from(Message message, Job job, Image image,
                                                     ReportReason reason, String description) {
            return new MessageReportFingerprint(
                    message.getSender().getId(),
                    job == null ? null : job.getId(),
                    message.getId(),
                    image == null ? null : image.getId(),
                    reason,
                    description);
        }

        private static MessageReportFingerprint from(Report report, Message persistedMessage) {
            return new MessageReportFingerprint(
                    persistedMessage.getSender().getId(),
                    report.getJob() == null ? null : report.getJob().getId(),
                    persistedMessage.getId(),
                    report.getImage() == null ? null : report.getImage().getId(),
                    report.getReason(),
                    report.getDescription() == null || report.getDescription().isBlank()
                            ? null
                            : report.getDescription().trim());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countReceivedBy(UUID userId) {
        return reportDao.countByReportedUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countMadeBy(UUID userId) {
        return reportDao.countByReporterId(userId);
    }
}
