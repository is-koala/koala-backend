package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.helpers.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/auth/login",
            "/auth/start-registration",
            "/auth/verify-registration-token",
            "/auth/complete-registration",
            "/auth/refresh-token",
            "/users/me/identity-verification");

    private final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter rateLimiter;
    private final HandlerExceptionResolver exceptionResolver;

    public RateLimitFilter(RateLimiter rateLimiter,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.rateLimiter = rateLimiter;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getMethod() + " " + request.getRequestURI() + " " + clientIp(request);

        if (!rateLimiter.tryAcquire(key)) {
            logger.warn("Rate limit exceeded for {} {}", request.getMethod(), request.getRequestURI());
            exceptionResolver.resolveException(request, response, null,
                    new TooManyRequestsException(AuthMessages.TOO_MANY_REQUESTS));
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_PATHS.contains(request.getRequestURI());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
