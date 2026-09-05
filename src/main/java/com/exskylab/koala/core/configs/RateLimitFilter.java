package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.TooManyRequestsException;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    private static final Set<String> AUTH_PATHS = Set.of(
            "/auth/login",
            "/auth/start-registration",
            "/auth/verify-registration-token",
            "/auth/complete-registration",
            "/auth/refresh-token",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/users/me/identity-verification");

    private static final List<PathPattern> UPLOAD_PATHS = List.of(
            PARSER.parse("/images"),
            PARSER.parse("/users/me/cv"),
            PARSER.parse("/users/me/profile-picture"),
            PARSER.parse("/companies"),
            PARSER.parse("/companies/{companyId}/images"),
            PARSER.parse("/jobs/{jobId}/images"),
            PARSER.parse("/admin/badges/{badgeId}/icon"));

    private static final PathPattern MESSAGE_PATH =
            PARSER.parse("/conversations/{conversationId}/messages");

    private static final PathPattern ADDRESS_SEARCH_PATH =
            PARSER.parse("/locations/address-search-sessions/**");
    private static final PathPattern ADDRESS_REVERSE_PATH =
            PARSER.parse("/locations/address-candidates/reverse");

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final HandlerExceptionResolver exceptionResolver;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.exceptionResolver = exceptionResolver;
    }

    private enum Bucket {
        AUTH, UPLOAD, MESSAGE, ADDRESS_SEARCH, WRITE, NONE
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = bucketOf(request);

        if (bucket != Bucket.NONE && !rateLimiter.tryAcquire(keyOf(bucket, request),
                settingsOf(bucket).getMaxAttempts(), settingsOf(bucket).getWindow())) {

            log.warn("Rate limit exceeded in bucket {} for {} {} from ip {}",
                    bucket, request.getMethod(), request.getRequestURI(), ClientIpResolver.resolve(request));

            exceptionResolver.resolveException(request, response, null,
                    new TooManyRequestsException(AuthMessages.TOO_MANY_REQUESTS));
            return;
        }

        filterChain.doFilter(request, response);
    }

    Bucket bucketOf(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (AUTH_PATHS.contains(path)) {
            return Bucket.AUTH;
        }

        PathContainer container = PathContainer.parsePath(path);

        if (ADDRESS_SEARCH_PATH.matches(container) || ADDRESS_REVERSE_PATH.matches(container)) {
            return Bucket.ADDRESS_SEARCH;
        }

        if (!WRITE_METHODS.contains(request.getMethod())) {
            return Bucket.NONE;
        }

        if (MESSAGE_PATH.matches(container)) {
            return Bucket.MESSAGE;
        }

        if (UPLOAD_PATHS.stream().anyMatch(pattern -> pattern.matches(container))) {
            return Bucket.UPLOAD;
        }

        return Bucket.WRITE;
    }

    private RateLimitProperties.Bucket settingsOf(Bucket bucket) {
        return switch (bucket) {
            case AUTH -> properties.getAuth();
            case UPLOAD -> properties.getUpload();
            case MESSAGE -> properties.getMessage();
            case ADDRESS_SEARCH -> properties.getAddressSearch();
            default -> properties.getWrite();
        };
    }

    private String keyOf(Bucket bucket, HttpServletRequest request) {
        String client = ClientIpResolver.resolve(request);

        if (bucket == Bucket.AUTH) {
            return "AUTH " + request.getMethod() + " " + request.getRequestURI() + " " + client;
        }

        return bucket + " " + client;
    }
}
