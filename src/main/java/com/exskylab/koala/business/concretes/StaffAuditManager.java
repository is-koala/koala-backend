package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.properties.AuditRetentionProperties;
import com.exskylab.koala.core.security.StaffAuditRecord;
import com.exskylab.koala.dataAccess.AdminAuditEntryDao;
import com.exskylab.koala.entities.AdminAuditEntry;
import com.exskylab.koala.entities.AdminAuditEntryDetail;
import com.exskylab.koala.entities.PermissionSensitivity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;

@Service
public class StaffAuditManager implements StaffAuditService {

    private static final int DISPLAY_NAME_LIMIT = 200;
    private static final int PERMISSION_LIMIT = 64;
    private static final int ACTION_LIMIT = 96;
    private static final int RESOURCE_TYPE_LIMIT = 64;
    private static final int RESOURCE_ID_LIMIT = 128;
    private static final int IP_LIMIT = 45;
    private static final int USER_AGENT_LIMIT = 512;
    private static final String UNKNOWN_ACTOR = "unknown";

    private final AdminAuditEntryDao adminAuditEntryDao;
    private final AuditRetentionProperties retentionProperties;

    public StaffAuditManager(AdminAuditEntryDao adminAuditEntryDao,
                             AuditRetentionProperties retentionProperties) {
        this.adminAuditEntryDao = adminAuditEntryDao;
        this.retentionProperties = retentionProperties;
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

    private int coreMonthsFor(PermissionSensitivity sensitivity) {
        return sensitivity == PermissionSensitivity.FINANCIAL
                ? retentionProperties.getFinancialCoreMonths()
                : retentionProperties.getCoreMonths();
    }

    private static Instant plusMonths(Instant from, int months) {
        return from.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
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
