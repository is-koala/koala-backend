package com.exskylab.koala.core.security;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Slf4j
public class StaffAuditFilter extends OncePerRequestFilter {

    private static final int MAX_CACHED_BODY_BYTES = 8 * 1024;
    private static final Set<String> BODYLESS_METHODS = Set.of("GET", "HEAD", "OPTIONS", "DELETE");

    private final StaffAuditService staffAuditService;

    public StaffAuditFilter(StaffAuditService staffAuditService) {
        this.staffAuditService = staffAuditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper cached = carriesAJsonBody(request)
                ? new ContentCachingRequestWrapper(request, MAX_CACHED_BODY_BYTES)
                : null;

        StaffAuditContext.open();
        try {
            chain.doFilter(cached == null ? request : cached, response);
        } finally {
            try {
                flush(cached);
            } finally {
                StaffAuditContext.close();
            }
        }
    }

    private void flush(ContentCachingRequestWrapper cached) {
        StaffAuditContext context = StaffAuditContext.current();
        if (context == null || context.pending() == null) {
            return;
        }

        try {
            staffAuditService.record(context.pending(), bodyOf(cached), context.before(), context.after());
        } catch (Exception exception) {
            log.error("Denetim kaydı yazılamadı: {} {}",
                    context.pending().action(), exception.getMessage());
        }
    }

    private static boolean carriesAJsonBody(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null || BODYLESS_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            return false;
        }
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    private static String bodyOf(ContentCachingRequestWrapper cached) {
        if (cached == null) {
            return null;
        }
        byte[] body = cached.getContentAsByteArray();
        return body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
    }
}
