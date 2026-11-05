package com.exskylab.koala.core.security;

import com.exskylab.koala.business.abstracts.AuditSubjectService;
import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.properties.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StaffAuditRecorderTest {

    private final StaffAuditService staffAuditService = mock(StaffAuditService.class);
    private final StaffAuditRecorder recorder = new StaffAuditRecorder(staffAuditService,
            mock(AuditSubjectService.class), new ClientIpResolver(new TrustedProxyProperties()));

    @RequiresPermission(action = "audit.event.record")
    void recordedAtAuthorization() {
    }

    @RequiresPermission(action = "audit.event.record", auditedByHandler = true)
    void recordedByTheHandler() {
    }

    @Test
    void aQueryParameterTurnsAnUnnamedPermissionWriteIntoAnAuditedSearch() throws Exception {
        recorder.observe(eventWithAQueryParameter(), annotationOf("recordedAtAuthorization"), staff(), true);

        verify(staffAuditService, times(1)).record(any(), any(), any(), any());
    }

    @Test
    void aMappingAuditedByItsHandlerIsNotRecordedAgainAtAuthorizationWhenTheCallIsAllowed() throws Exception {
        recorder.observe(eventWithAQueryParameter(), annotationOf("recordedByTheHandler"), staff(), true);

        verify(staffAuditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void aCallRefusedAtAuthorizationIsRecordedEvenWhenItsHandlerAuditsTheMapping() throws Exception {
        recorder.observe(eventWithAQueryParameter(), annotationOf("recordedByTheHandler"), staff(), false);

        verify(staffAuditService, times(1)).record(any(), any(), any(), any());
    }

    private static MockHttpServletRequest eventWithAQueryParameter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/audit-log/events");
        request.setParameter("x", "1");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/admin/audit-log/events");
        return request;
    }

    private static RequiresPermission annotationOf(String method) throws NoSuchMethodException {
        return StaffAuditRecorderTest.class.getDeclaredMethod(method).getAnnotation(RequiresPermission.class);
    }

    private static UsernamePasswordAuthenticationToken staff() {
        StaffPrincipal principal = new StaffPrincipal(UUID.randomUUID(), "denetim@iskoala.com", "Denetim", Set.of(),
                Set.of(), false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
