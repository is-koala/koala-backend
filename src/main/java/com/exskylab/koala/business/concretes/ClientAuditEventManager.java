package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuditSubjectService;
import com.exskylab.koala.business.abstracts.ClientAuditEventService;
import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.constants.AuditMessages;
import com.exskylab.koala.core.dtos.audit.request.ClientAuditEventDto;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.AuditScope;
import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.RateLimitProperties;
import com.exskylab.koala.core.security.AuditSubject;
import com.exskylab.koala.core.security.AuditSubjects;
import com.exskylab.koala.core.security.StaffAuditRecord;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.AdminAuditEntry;
import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffExportList;
import com.exskylab.koala.entities.StaffPermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class ClientAuditEventManager implements ClientAuditEventService {

    static final String BUDGET = "audit-event";

    private final StaffAuditService staffAuditService;
    private final AuditSubjectService auditSubjectService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public ClientAuditEventManager(StaffAuditService staffAuditService, AuditSubjectService auditSubjectService,
                                   RateLimiter rateLimiter, RateLimitProperties rateLimitProperties,
                                   ObjectMapper objectMapper) {
        this.staffAuditService = staffAuditService;
        this.auditSubjectService = auditSubjectService;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(noRollbackFor = ForbiddenException.class)
    public AdminAuditEntry record(StaffPrincipal actor, ClientAuditEventDto event, String ip, String userAgent) {
        if (actor == null || !actor.isRegistered()) {
            log.warn("Client audit event refused: the caller is not a registered staff member (bridge={})",
                    actor == null);
            throw new ForbiddenException(AuditMessages.EVENT_STAFF_ONLY);
        }

        RateLimitProperties.Bucket budget = rateLimitProperties.getAuditEvent();
        if (!rateLimiter.tryAcquire(BUDGET + ":" + actor.staffId(), budget.getMaxAttempts(), budget.getWindow())) {
            log.warn("Client audit event refused, budget exhausted: staff={} action={} list={}",
                    actor.staffId(), event.getAction(), event.getResourceType());
            throw new TooManyRequestsException(AuditMessages.EVENT_TOO_MANY);
        }

        StaffExportList list = event.getResourceType();
        StaffPermission permission = list.getPermission();
        AuditSubjects subjects = subjectsOf(event);
        if (!actor.holds(permission)) {
            AdminAuditEntry denied = write(actor, event, false, exists(subjects) ? subjects : AuditSubjects.NONE,
                    ip, userAgent);
            log.warn("Client audit event refused, the caller lacks the list permission: staff={} list={} "
                    + "permission={} entry={}", actor.staffId(), list, permission.getCode(), denied.getId());
            throw new ForbiddenException(AuditMessages.EVENT_LIST_PERMISSION_MISSING);
        }

        if (!exists(subjects)) {
            log.warn("Client audit event refused, the reported subject does not exist: staff={} list={}",
                    actor.staffId(), event.getResourceType());
            throw new ValidationException(AuditMessages.EVENT_SUBJECT_NOT_FOUND);
        }

        AdminAuditEntry entry = write(actor, event, true, subjects, ip, userAgent);
        boolean piiIncluded = Boolean.TRUE.equals(event.getPiiIncluded());
        log.info("Client audit event recorded: staff={} action={} list={} rows={} pii={} entry={}",
                actor.staffId(), event.getAction(), list, event.getRowCount(), piiIncluded, entry.getId());
        return entry;
    }

    private AdminAuditEntry write(StaffPrincipal actor, ClientAuditEventDto event, boolean allowed,
                                  AuditSubjects subjects, String ip, String userAgent) {
        StaffExportList list = event.getResourceType();
        StaffPermission permission = list.getPermission();
        boolean piiIncluded = Boolean.TRUE.equals(event.getPiiIncluded());
        return staffAuditService.recordClientEvent(new StaffAuditRecord(
                actor.staffId(),
                actor.displayName() == null || actor.displayName().isBlank() ? actor.email() : actor.displayName(),
                permission.getCode(),
                event.getAction().getCode(),
                list.getResourceType(),
                AuditScope.NO_RECORD,
                allowed,
                false,
                sensitivityOf(permission, piiIncluded),
                ip,
                userAgent,
                subjects), objectMapper.writeValueAsString(event), event.getRowCount(), piiIncluded);
    }

    private boolean exists(AuditSubjects subjects) {
        return (subjects.subject() == null || auditSubjectService.exists(subjects.subject()))
                && (subjects.related() == null || auditSubjectService.exists(subjects.related()));
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
        AuditSubject subject = new AuditSubject(event.getSubjectType(), event.getSubjectId());
        if (event.getRelatedSubjectType() == null || event.getRelatedSubjectId() == null) {
            return AuditSubjects.of(subject);
        }
        return AuditSubjects.of(subject, new AuditSubject(event.getRelatedSubjectType(), event.getRelatedSubjectId()));
    }
}
