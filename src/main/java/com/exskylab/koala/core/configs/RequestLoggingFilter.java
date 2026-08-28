package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.helpers.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = "app.userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            Object userId = request.getAttribute(USER_ID_ATTRIBUTE);

            String line = "{} {} -> {} ({} ms) user={} ip={}";
            Object[] args = {request.getMethod(), request.getRequestURI(), status, durationMs,
                    userId == null ? "anonymous" : userId, ClientIpResolver.resolve(request)};

            if (status >= 500) {
                log.error(line, args);
            } else if (status >= 400) {
                log.warn(line, args);
            } else {
                log.info(line, args);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }
}
