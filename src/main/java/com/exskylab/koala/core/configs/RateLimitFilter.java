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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HexFormat;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;
    private static final int MAX_AUTH_BODY_BYTES = 16 * 1024;

    private static final Set<String> AUTH_PATHS = Set.of(
            "/auth/login",
            "/auth/start-registration",
            "/auth/verify-registration-token",
            "/auth/complete-registration",
            "/auth/refresh-token",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/users/me/identity-verification",
            "/admin/auth/login",
            "/admin/auth/refresh",
            "/admin/auth/password");

    private static final List<PathPattern> UPLOAD_PATHS = List.of(
            PARSER.parse("/images"),
            PARSER.parse("/users/me/cv"),
            PARSER.parse("/users/me/profile-picture"),
            PARSER.parse("/companies"),
            PARSER.parse("/companies/{companyId}/images"),
            PARSER.parse("/jobs/{jobId}/images"),
            PARSER.parse("/admin/badges/{badgeId}/icon"),
            PARSER.parse("/disputes/{disputeId}/evidence"));

    private static final PathPattern MESSAGE_PATH =
            PARSER.parse("/conversations/{conversationId}/messages");

    private static final PathPattern REALTIME_CREDENTIAL_PATH =
            PARSER.parse("/users/me/realtime-credential");

    private static final PathPattern ADDRESS_SEARCH_PATH =
            PARSER.parse("/locations/address-search-sessions/**");
    private static final PathPattern ADDRESS_REVERSE_PATH =
            PARSER.parse("/locations/address-candidates/reverse");

    private static final List<PathPattern> PUBLIC_PATHS = List.of(
            PARSER.parse("/jobs"),
            PARSER.parse("/jobs/{jobId}"),
            PARSER.parse("/jobs/{jobId}/images"));

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final HandlerExceptionResolver exceptionResolver;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
                           ClientIpResolver clientIpResolver, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.exceptionResolver = exceptionResolver;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    private enum Bucket {
        AUTH, UPLOAD, MESSAGE, REALTIME_CREDENTIAL, PUBLIC, WRITE, NONE
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = bucketOf(request);
        PreparedRequest prepared = bucket == Bucket.AUTH ? prepareAuthRequest(request)
                : new PreparedRequest(request, "unknown", false);

        if (prepared.tooLarge()) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }

        if (bucket != Bucket.NONE && !rateLimiter.tryAcquire(keyOf(bucket, prepared.request(), prepared.target()),
                settingsOf(bucket).getMaxAttempts(), settingsOf(bucket).getWindow())) {

            log.warn("Rate limit exceeded in bucket {} for {} {} from ip {}",
                    bucket, request.getMethod(), request.getRequestURI(), clientIpResolver.resolve(request));

            exceptionResolver.resolveException(request, response, null,
                    new TooManyRequestsException(AuthMessages.TOO_MANY_REQUESTS));
            return;
        }

        filterChain.doFilter(prepared.request(), response);
    }

    Bucket bucketOf(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (AUTH_PATHS.contains(path)) {
            return Bucket.AUTH;
        }

        PathContainer container = PathContainer.parsePath(path);

        if (ADDRESS_SEARCH_PATH.matches(container) || ADDRESS_REVERSE_PATH.matches(container)
                || "GET".equals(request.getMethod())
                && PUBLIC_PATHS.stream().anyMatch(pattern -> pattern.matches(container))) {
            return Bucket.PUBLIC;
        }

        if (!WRITE_METHODS.contains(request.getMethod())) {
            return Bucket.NONE;
        }

        if (MESSAGE_PATH.matches(container)) {
            return Bucket.MESSAGE;
        }

        if (REALTIME_CREDENTIAL_PATH.matches(container)) {
            return Bucket.REALTIME_CREDENTIAL;
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
            case REALTIME_CREDENTIAL -> properties.getRealtimeCredential();
            case PUBLIC -> properties.getPublic();
            default -> properties.getWrite();
        };
    }

    private String keyOf(Bucket bucket, HttpServletRequest request, String target) {
        String client = clientIpResolver.resolve(request);

        if (bucket == Bucket.AUTH) {
            return "AUTH " + request.getMethod() + " " + request.getRequestURI() + " " + client + " " + target;
        }

        return bucket + " " + client;
    }

    private PreparedRequest prepareAuthRequest(HttpServletRequest request) throws IOException {
        if (request.getContentType() == null || !request.getContentType().startsWith("application/json")) {
            return new PreparedRequest(request, "unknown", false);
        }
        if (request.getContentLengthLong() > MAX_AUTH_BODY_BYTES) {
            return new PreparedRequest(request, "unknown", true);
        }
        byte[] body = request.getInputStream().readNBytes(MAX_AUTH_BODY_BYTES + 1);
        if (body.length > MAX_AUTH_BODY_BYTES) {
            return new PreparedRequest(request, "unknown", true);
        }
        CachedBodyRequest wrapped = new CachedBodyRequest(request, body);
        try {
            JsonNode root = objectMapper.readTree(body);
            for (String field : List.of("email", "phoneNumber", "phone", "refreshToken",
                    "registrationToken", "setPasswordToken", "resetToken", "tcIdentityNumber")) {
                JsonNode value = root.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    String normalized = value.asText().trim().toLowerCase(Locale.ROOT);
                    return new PreparedRequest(wrapped, digest(normalized), false);
                }
            }
        } catch (RuntimeException exception) {
            return new PreparedRequest(wrapped, "unknown", false);
        }
        return new PreparedRequest(wrapped, "unknown", false);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record PreparedRequest(HttpServletRequest request, String target, boolean tooLarge) {
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
