package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.IdentityRevealService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.IdentityRevealMessages;
import com.exskylab.koala.core.dtos.identity.response.AdminIdentityRevealDto;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.mappers.IdentityRevealMapper;
import com.exskylab.koala.core.properties.RateLimitProperties;
import com.exskylab.koala.dataAccess.IdentityRevealDao;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.CancellationParty;
import com.exskylab.koala.entities.IdentityReveal;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IdentityRevealManager implements IdentityRevealService {

    static final Duration NOTIFICATION_QUIET_PERIOD = Duration.ofHours(24);
    static final String RATE_LIMIT_BUDGET = "identity-reveal";
    private static final Set<String> SORTABLE = Set.of("revealedAt");

    private final IdentityRevealDao identityRevealDao;
    private final JobAssignmentService jobAssignmentService;
    private final UserService userService;
    private final SecurityService securityService;
    private final AssignmentParticipants participants;
    private final ActivityNotificationService activityNotificationService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final PageableSanitizer pageableSanitizer;
    private final IdentityRevealMapper identityRevealMapper;
    private final Clock clock;

    public IdentityRevealManager(IdentityRevealDao identityRevealDao,
                                 JobAssignmentService jobAssignmentService,
                                 UserService userService,
                                 SecurityService securityService,
                                 AssignmentParticipants participants,
                                 ActivityNotificationService activityNotificationService,
                                 RateLimiter rateLimiter,
                                 RateLimitProperties rateLimitProperties,
                                 PageableSanitizer pageableSanitizer,
                                 IdentityRevealMapper identityRevealMapper,
                                 Clock clock) {
        this.identityRevealDao = identityRevealDao;
        this.jobAssignmentService = jobAssignmentService;
        this.userService = userService;
        this.securityService = securityService;
        this.participants = participants;
        this.activityNotificationService = activityNotificationService;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.pageableSanitizer = pageableSanitizer;
        this.identityRevealMapper = identityRevealMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public User revealWorkerIdentity(UUID assignmentId, String clientIpAddress) {
        JobAssignment assignment = jobAssignmentService.getDetailedById(assignmentId);
        UUID actorId = securityService.getAuthenticatedUserId();

        CancellationParty party = participants.partyOf(assignment, actorId).orElse(null);
        if (party != CancellationParty.EMPLOYER) {
            throw Concealment.notFound(JobAssignmentMessages.NOT_FOUND, actorId, assignmentId,
                    "not the employer of the assignment");
        }
        if (!AssignmentStatus.HIRED.contains(assignment.getStatus())) {
            throw Concealment.notFound(JobAssignmentMessages.NOT_FOUND, actorId, assignmentId,
                    "assignment is not a hire yet: " + assignment.getStatus());
        }

        User worker = assignment.getWorker();
        if (!worker.isIdentityVerified()) {
            throw new ConflictException(IdentityRevealMessages.WORKER_NOT_VERIFIED);
        }

        RateLimitProperties.Bucket bucket = rateLimitProperties.getIdentityReveal();
        if (!rateLimiter.tryAcquire(RATE_LIMIT_BUDGET + ":" + actorId, bucket.getMaxAttempts(), bucket.getWindow())) {
            log.warn("Worker identity reveal refused, hourly budget exhausted: viewer={} assignment={}",
                    actorId, assignmentId);
            throw new TooManyRequestsException(IdentityRevealMessages.TOO_MANY_REQUESTS);
        }

        Instant now = clock.instant();
        String maskedIp = LogMasks.ip(clientIpAddress);
        boolean recentlyRevealed = identityRevealDao.existsByViewer_IdAndJobAssignment_IdAndRevealedAtAfter(
                actorId, assignmentId, now.minus(NOTIFICATION_QUIET_PERIOD));

        IdentityReveal reveal = new IdentityReveal();
        reveal.setViewer(userService.getReference(actorId));
        reveal.setWorker(worker);
        reveal.setJobAssignment(assignment);
        reveal.setRevealedAt(now);
        reveal.setIpAddress(maskedIp);
        identityRevealDao.save(reveal);

        log.info("Worker identity revealed: viewer={} worker={} assignment={} ip={} notified={}",
                actorId, worker.getId(), assignmentId, maskedIp, !recentlyRevealed);

        if (!recentlyRevealed) {
            activityNotificationService.identityRevealed(assignment);
        }
        return worker;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminIdentityRevealDto> listForStaff(UUID workerId, Pageable pageable) {
        pageableSanitizer.rejectUnknownSort(pageable, SORTABLE);
        userService.getById(workerId);
        Page<IdentityReveal> page = identityRevealDao.findAllByWorker_Id(workerId,
                pageableSanitizer.sanitize(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "revealedAt")));
        Map<UUID, User> viewers = new HashMap<>();
        for (User viewer : userService.getAllByIdIn(page.getContent().stream()
                .map(reveal -> reveal.getViewer().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new)))) {
            viewers.put(viewer.getId(), viewer);
        }
        return page.map(reveal -> identityRevealMapper.toAdminIdentityRevealDto(reveal,
                viewers.get(reveal.getViewer().getId())));
    }
}
