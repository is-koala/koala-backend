package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.logging.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String ACCESS_LOGGER_NAME = "http.access";
    public static final String USER_ID_ATTRIBUTE = "app.userId";

    private static final Logger accessLog = LoggerFactory.getLogger(ACCESS_LOGGER_NAME);

    private final ClientIpResolver clientIpResolver;

    public RequestLoggingFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        LogContext.put(LogContext.METHOD, request.getMethod());
        LogContext.put(LogContext.PATH, request.getRequestURI());
        LogContext.put(LogContext.CLIENT_IP, clientIpResolver.resolve(request));

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            Object userId = request.getAttribute(USER_ID_ATTRIBUTE);

            LogContext.put(LogContext.STATUS, status);
            LogContext.put(LogContext.DURATION_MS, durationMs);
            LogContext.put(LogContext.USER_ID, userId);

            String line = "{} {} -> {} ({} ms) user={} ip={}";
            Object[] args = {request.getMethod(), request.getRequestURI(), status, durationMs,
                    userId == null ? "anonymous" : userId, clientIpResolver.resolve(request)};

            if (status >= 500) {
                accessLog.error(line, args);
            } else if (status >= 400) {
                accessLog.warn(line, args);
            } else {
                accessLog.info(line, args);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }
}
