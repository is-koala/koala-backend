package com.exskylab.koala.core.security;

import com.exskylab.koala.core.properties.StaffBridgeProperties;
import com.exskylab.koala.entities.Role;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.function.Supplier;

@Component
@Slf4j
public class StaffPermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);
    private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final StaffAuditRecorder staffAuditRecorder;
    private final StaffBridgeProperties staffBridgeProperties;

    public StaffPermissionAuthorizationManager(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
            StaffAuditRecorder staffAuditRecorder, StaffBridgeProperties staffBridgeProperties) {
        this.handlerMappings = handlerMappings;
        this.staffAuditRecorder = staffAuditRecorder;
        this.staffBridgeProperties = staffBridgeProperties;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {

        HttpServletRequest request = context.getRequest();
        RequiresPermission required = requiredBy(request);
        if (required == null) {
            log.warn("Admin request without an authorization annotation was denied: {} {}",
                    request.getMethod(), request.getRequestURI());
            return DENIED;
        }

        Authentication current = authentication.get();
        AuthorizationDecision decision = decide(current, required);

        staffAuditRecorder.observe(request, required, current, decision.isGranted());

        return decision;
    }

    private AuthorizationDecision decide(Authentication current, RequiresPermission required) {
        if (current == null || !current.isAuthenticated()) {
            return DENIED;
        }

        if (current.getPrincipal() instanceof StaffPrincipal staff) {
            return decideForStaff(staff, required);
        }

        if (!staffBridgeProperties.isOpen()) {
            return DENIED;
        }

        boolean marketplaceAdmin = current.getAuthorities().stream()
                .anyMatch(authority -> Role.ROLE_ADMIN.getAuthority().equals(authority.getAuthority()));

        return marketplaceAdmin ? GRANTED : DENIED;
    }

    private static AuthorizationDecision decideForStaff(StaffPrincipal staff, RequiresPermission required) {
        if (!staff.isRegistered() || staff.mustChangePassword()) {
            return DENIED;
        }
        if (required.value().length == 0) {
            return GRANTED;
        }
        return staff.permissions().containsAll(Arrays.asList(required.value())) ? GRANTED : DENIED;
    }

    private RequiresPermission requiredBy(HttpServletRequest request) {
        RequestMappingHandlerMapping mapping = handlerMappings.getIfAvailable();
        if (mapping == null) {
            return null;
        }

        try {
            HandlerExecutionChain chain = mapping.getHandler(request);
            if (chain == null || !(chain.getHandler() instanceof HandlerMethod handler)) {
                return null;
            }

            RequiresPermission onMethod =
                    AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequiresPermission.class);
            return onMethod != null
                    ? onMethod
                    : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequiresPermission.class);
        } catch (Exception exception) {
            log.debug("Admin request could not be bound to a handler: {}", exception.getMessage());
            return null;
        }
    }
}
