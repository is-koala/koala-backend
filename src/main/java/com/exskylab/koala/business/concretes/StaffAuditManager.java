package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.helpers.CreatedAtRange;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
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

    private static final Set<String> SORTABLE = Set.of("occurredAt");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt");

    private final AdminAuditEntryDao adminAuditEntryDao;
    private final AdminAuditEntryDetailDao adminAuditEntryDetailDao;
    private final AuditRetentionProperties retentionProperties;
    private final PageableSanitizer pageableSanitizer;

    public StaffAuditManager(AdminAuditEntryDao adminAuditEntryDao,
                             AdminAuditEntryDetailDao adminAuditEntryDetailDao,
                             AuditRetentionProperties retentionProperties,
                             PageableSanitizer pageableSanitizer) {
        this.adminAuditEntryDao = adminAuditEntryDao;
        this.adminAuditEntryDetailDao = adminAuditEntryDetailDao;
        this.retentionProperties = retentionProperties;
        this.pageableSanitizer = pageableSanitizer;
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
    @Transactional(readOnly = true)
    public Page<AdminAuditEntry> search(UUID actor, String action, String permission, String resourceType,
                                        String resourceId, AuditSubjectType subjectType, UUID subjectId,
                                        Instant from, Instant to, Boolean allowed, Pageable pageable) {
        pageableSanitizer.rejectUnknownSort(pageable, SORTABLE);
        Pageable page = pageableSanitizer.sanitize(pageable, SORTABLE, NEWEST_FIRST);

        CreatedAtRange occurredAt = CreatedAtRange.of(from, to);
        return adminAuditEntryDao.search(actor, blankToNull(action), blankToNull(permission),
                blankToNull(resourceType), blankToNull(resourceId),
                subjectType == null ? null : subjectType.name(), subjectId,
                occurredAt.from(), occurredAt.to(),
                allowed != null, Boolean.TRUE.equals(allowed), page);
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
        int erased = adminAuditEntryDetailDao.deleteByResourceId(resourceId);
        if (erased > 0) {
            log.info("Audit detail rows erased on request for resource {}: {}", resourceId, erased);
        }
        return erased;
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
