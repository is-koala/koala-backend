package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.constants.AuditMessages;
import com.exskylab.koala.core.dtos.audit.request.ClientAuditEventDto;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.helpers.AuditScope;
import com.exskylab.koala.core.helpers.CreatedAtRange;
import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.RateLimitProperties;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.properties.AuditRetentionProperties;
import com.exskylab.koala.core.security.AuditSubject;
import com.exskylab.koala.core.security.AuditSubjects;
import com.exskylab.koala.core.security.StaffAuditRecord;
import com.exskylab.koala.dataAccess.AdminAuditEntryDao;
import com.exskylab.koala.dataAccess.AdminAuditEntryDetailDao;
import com.exskylab.koala.entities.AdminAuditEntry;
import com.exskylab.koala.entities.AdminAuditEntryDetail;
import com.exskylab.koala.entities.AuditSubjectType;
import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffExportList;
import com.exskylab.koala.entities.StaffPermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class StaffAuditManager implements StaffAuditService {

    private static final int DISPLAY_NAME_LIMIT = 200;
    private static final int PERMISSION_LIMIT = 64;
    private static final int ACTION_LIMIT = 96;
    private static final int RESOURCE_TYPE_LIMIT = 64;
    private static final int RESOURCE_ID_LIMIT = 128;
    private static final int IP_LIMIT = 45;
    private static final int USER_AGENT_LIMIT = 512;
    private static final String UNKNOWN_ACTOR = "unknown";
    static final String CLIENT_EVENT_BUDGET = "audit-event";

    private static final Set<String> SORTABLE = Set.of("occurredAt");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt");

    private final AdminAuditEntryDao adminAuditEntryDao;
    private final AdminAuditEntryDetailDao adminAuditEntryDetailDao;
    private final AuditRetentionProperties retentionProperties;
    private final PageableSanitizer pageableSanitizer;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public StaffAuditManager(AdminAuditEntryDao adminAuditEntryDao,
                             AdminAuditEntryDetailDao adminAuditEntryDetailDao,
                             AuditRetentionProperties retentionProperties,
                             PageableSanitizer pageableSanitizer,
                             RateLimiter rateLimiter,
                             RateLimitProperties rateLimitProperties,
                             ObjectMapper objectMapper) {
        this.adminAuditEntryDao = adminAuditEntryDao;
        this.adminAuditEntryDetailDao = adminAuditEntryDetailDao;
        this.retentionProperties = retentionProperties;
        this.pageableSanitizer = pageableSanitizer;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AdminAuditEntry record(StaffAuditRecord source, String requestBody, String before, String after) {
        Instant now = Instant.now();

        AdminAuditEntry entry = new AdminAuditEntry();
        entry.setOccurredAt(now);
        entry.setActorStaffId(source.actorStaffId());
        entry.setActorDisplayName(truncate(orUnknown(source.actorDisplayName()), DISPLAY_NAME_LIMIT));
        entry.setPermission(truncate(source.permission(), PERMISSION_LIMIT));
        entry.setAction(truncate(source.action(), ACTION_LIMIT));
        entry.setResourceType(truncate(source.resourceType(), RESOURCE_TYPE_LIMIT));
        entry.setResourceId(truncate(source.resourceId() == null ? "" : source.resourceId(), RESOURCE_ID_LIMIT));
        entry.setAllowed(source.allowed());
        entry.setMutating(source.mutating());
        entry.setSensitivity(source.sensitivity());
        entry.setRetainUntil(plusMonths(now, coreMonthsFor(source.sensitivity())));
        stampSubjects(entry, source.subjects());

        AdminAuditEntryDetail detail = new AdminAuditEntryDetail();
        detail.setEntry(entry);
        detail.setRequestBody(requestBody);
        detail.setIp(truncate(source.ip(), IP_LIMIT));
        detail.setUserAgent(truncate(source.userAgent(), USER_AGENT_LIMIT));
        detail.setBeforeValue(before);
        detail.setAfterValue(after);
        detail.setPurgeAfter(plusMonths(now, retentionProperties.getDetailMonths()));
        entry.setDetail(detail);

        return adminAuditEntryDao.save(entry);
    }

    @Override
    @Transactional(noRollbackFor = ForbiddenException.class)
    public AdminAuditEntry recordClientEvent(StaffPrincipal actor, ClientAuditEventDto event, String ip,
                                             String userAgent) {
        if (actor == null || !actor.isRegistered()) {
            log.warn("Client audit event refused: the caller is not a registered staff member");
            throw new ForbiddenException(AuditMessages.EVENT_LIST_PERMISSION_MISSING);
        }

        RateLimitProperties.Bucket budget = rateLimitProperties.getAuditEvent();
        if (!rateLimiter.tryAcquire(CLIENT_EVENT_BUDGET + ":" + actor.staffId(),
                budget.getMaxAttempts(), budget.getWindow())) {
            log.warn("Client audit event refused, budget exhausted: staff={} action={} list={}",
                    actor.staffId(), event.getAction(), event.getResourceType());
            throw new TooManyRequestsException(AuditMessages.EVENT_TOO_MANY);
        }

        StaffExportList list = event.getResourceType();
        StaffPermission permission = list.getPermission();
        boolean allowed = actor.holds(permission);

        AdminAuditEntry entry = record(new StaffAuditRecord(
                actor.staffId(),
                actor.displayName() == null || actor.displayName().isBlank() ? actor.email() : actor.displayName(),
                permission.getCode(),
                event.getAction().getCode(),
                list.getResourceType(),
                AuditScope.NO_RECORD,
                allowed,
                false,
                sensitivityOf(permission, Boolean.TRUE.equals(event.getPiiIncluded())),
                ip,
                userAgent,
                subjectsOf(event)), objectMapper.writeValueAsString(event), null, null);

        if (!allowed) {
            log.warn("Client audit event refused, the caller lacks the list permission: staff={} list={} "
                    + "permission={} entry={}", actor.staffId(), list, permission.getCode(), entry.getId());
            throw new ForbiddenException(AuditMessages.EVENT_LIST_PERMISSION_MISSING);
        }

        log.info("Client audit event recorded: staff={} action={} list={} rows={} pii={} entry={}",
                actor.staffId(), event.getAction(), list, event.getRowCount(), event.getPiiIncluded(), entry.getId());
        return entry;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminAuditEntry> search(UUID actor, String action, String permission, String resourceType,
                                        String resourceId, AuditSubjectType subjectType, UUID subjectId,
                                        Instant from, Instant to, Boolean allowed, Boolean mutating,
                                        Pageable pageable) {
        pageableSanitizer.rejectUnknownSort(pageable, SORTABLE);
        Pageable page = pageableSanitizer.sanitize(pageable, SORTABLE, NEWEST_FIRST);

        CreatedAtRange occurredAt = CreatedAtRange.of(from, to);
        return adminAuditEntryDao.search(actor, blankToNull(action), blankToNull(permission),
                blankToNull(resourceType), blankToNull(resourceId),
                subjectType == null ? null : subjectType.name(), subjectId,
                occurredAt.from(), occurredAt.to(),
                allowed != null, Boolean.TRUE.equals(allowed),
                mutating != null, Boolean.TRUE.equals(mutating), page);
    }

    @Override
    @Transactional
    public int purgeExpiredDetails() {
        int purged = adminAuditEntryDetailDao.deleteExpired(Instant.now());
        if (purged > 0) {
            log.info("Audit detail rows past their retention window removed: {}", purged);
        }
        return purged;
    }

    @Override
    @Transactional
    public int purgeExpiredEntries() {
        int purged = adminAuditEntryDao.deleteExpired(Instant.now());
        if (purged > 0) {
            log.info("Audit core rows past their retention window removed: {}", purged);
        }
        return purged;
    }

    @Override
    @Transactional
    public int eraseDetailsForResource(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return 0;
        }
        int erased = personIdOf(resourceId)
                .map(personId -> adminAuditEntryDetailDao.deleteAboutPerson(resourceId, personId))
                .orElseGet(() -> adminAuditEntryDetailDao.deleteByResourceId(resourceId));
        if (erased > 0) {
            log.info("Audit detail rows erased on anonymization for resource {}: {}", resourceId, erased);
        }
        return erased;
    }

    private static PermissionSensitivity sensitivityOf(StaffPermission permission, boolean piiIncluded) {
        PermissionSensitivity listSensitivity = permission.getSensitivity();
        if (piiIncluded && listSensitivity.getRank() < PermissionSensitivity.PERSONAL_DATA.getRank()) {
            return PermissionSensitivity.PERSONAL_DATA;
        }
        return listSensitivity;
    }

    private static AuditSubjects subjectsOf(ClientAuditEventDto event) {
        if (event.getSubjectType() == null || event.getSubjectId() == null) {
            return AuditSubjects.NONE;
        }
        return AuditSubjects.of(new AuditSubject(event.getSubjectType(), event.getSubjectId()));
    }

    private static void stampSubjects(AdminAuditEntry entry, AuditSubjects subjects) {
        AuditSubject subject = subjects.subject();
        if (subject != null) {
            entry.setSubjectType(subject.type());
            entry.setSubjectId(subject.id());
        }
        AuditSubject related = subjects.related();
        if (related != null) {
            entry.setRelatedSubjectType(related.type());
            entry.setRelatedSubjectId(related.id());
        }
    }

    private static Optional<UUID> personIdOf(String resourceId) {
        try {
            return Optional.of(UUID.fromString(resourceId));
        } catch (IllegalArgumentException notAPerson) {
            return Optional.empty();
        }
    }

    private int coreMonthsFor(PermissionSensitivity sensitivity) {
        return sensitivity == PermissionSensitivity.FINANCIAL
                ? retentionProperties.getFinancialCoreMonths()
                : retentionProperties.getCoreMonths();
    }

    private static Instant plusMonths(Instant from, int months) {
        return from.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN_ACTOR : value;
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
