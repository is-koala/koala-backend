package com.exskylab.koala.core.security;

import com.exskylab.koala.business.abstracts.AuditSubjectService;
import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.helpers.AuditScope;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffPermission;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class StaffAuditRecorder {

    private static final String NO_PERMISSION = "none";
    private static final String UNNAMED_ACTION = "admin.request";
    private static final String USER_AGENT_HEADER = "User-Agent";

    private final StaffAuditService staffAuditService;
    private final AuditSubjectService auditSubjectService;
    private final ClientIpResolver clientIpResolver;

    public StaffAuditRecorder(StaffAuditService staffAuditService, AuditSubjectService auditSubjectService,
                              ClientIpResolver clientIpResolver) {
        this.staffAuditService = staffAuditService;
        this.auditSubjectService = auditSubjectService;
        this.clientIpResolver = clientIpResolver;
    }

    private record Actor(UUID id, String displayName) {
    }

    public void observe(HttpServletRequest request, RequiresPermission required,
                        Authentication authentication, boolean allowed) {
        if (required.auditedByHandler()) {
            return;
        }

        StaffPermission[] permissions = required.value();
        String pattern = patternOf(request);
        String resourceId = AuditScope.resourceIdOf(pattern, variablesOf(request));
        boolean mutating = AuditScope.isMutating(request.getMethod(), permissions);
        PermissionSensitivity sensitivity = AuditScope.sensitivityOf(permissions);

        if (!AuditScope.isAuditable(allowed, mutating, sensitivity,
                !resourceId.isEmpty(), AuditScope.hasFilter(request.getParameterMap()))) {
            return;
        }

        Actor actor = actorOf(authentication);
        if (actor == null) {
            return;
        }

        String resourceType = AuditScope.resourceTypeOf(pattern);
        StaffAuditRecord record = new StaffAuditRecord(
                actor.id(),
                actor.displayName(),
                permissionCodeOf(permissions),
                actionOf(required, permissions),
                resourceType,
                resourceId,
                allowed,
                mutating,
                sensitivity,
                clientIpResolver.resolve(request),
                request.getHeader(USER_AGENT_HEADER),
                AuditScope.carriesASubject(mutating, sensitivity)
                        ? subjectsOf(resourceType, resourceId)
                        : AuditSubjects.NONE);

        if (StaffAuditContext.pend(record)) {
            return;
        }

        try {
            staffAuditService.record(record, null, null, null);
        } catch (Exception exception) {
            log.error("Audit record could not be written: {}", record.action(), exception);
        }
    }

    private AuditSubjects subjectsOf(String resourceType, String resourceId) {
        try {
            return auditSubjectService.resolve(resourceType, resourceId);
        } catch (Exception exception) {
            log.warn("Audit subject could not be resolved for {} {}", resourceType, resourceId, exception);
            return AuditSubjects.NONE;
        }
    }

    private static Actor actorOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof StaffPrincipal staff) {
            String name = staff.displayName() == null || staff.displayName().isBlank()
                    ? staff.email()
                    : staff.displayName();
            return new Actor(staff.staffId(), name);
        }

        if (principal instanceof UserPrincipal user) {
            return new Actor(user.id(), user.email());
        }

        String name = authentication.getName();
        return name == null || name.isBlank() ? null : new Actor(null, name);
    }

    private static String permissionCodeOf(StaffPermission[] permissions) {
        return permissions.length == 0 ? NO_PERMISSION : permissions[0].getCode();
    }

    private static String actionOf(RequiresPermission required, StaffPermission[] permissions) {
        if (!required.action().isBlank()) {
            return required.action();
        }
        return permissions.length == 0 ? UNNAMED_ACTION : permissions[0].getCode();
    }

    private static String patternOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern instanceof String declared ? declared : request.getRequestURI();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> variablesOf(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> declared ? (Map<String, String>) declared : Map.of();
    }
}
