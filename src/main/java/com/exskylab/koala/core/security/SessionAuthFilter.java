package com.exskylab.koala.core.security;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.configs.RequestLoggingFilter;
import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.helpers.DeviceIdentityRequest;
import com.exskylab.koala.core.logging.LogContext;
import com.exskylab.koala.core.exceptions.UnauthorizedException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.entities.Device;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import com.exskylab.koala.entities.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final Set<String> SELF_AUTHENTICATING_PATHS = Set.of("/auth/refresh-token");


    private static final Duration LAST_ACCESS_WRITE_INTERVAL = Duration.ofMinutes(5);

    private final SessionService sessionService;
    private final JwtService jwtService;
    private final DeviceService deviceService;

    private final HandlerExceptionResolver exceptionResolver;


    public SessionAuthFilter(SessionService sessionService, JwtService jwtService, DeviceService deviceService,
                             @Qualifier("handlerExceptionResolver")HandlerExceptionResolver exceptionResolver) {
        this.sessionService = sessionService;
        this.jwtService = jwtService;
        this.deviceService = deviceService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.debug("Processing authentication for request: {}", request.getRequestURI());
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No valid Authorization header found in request: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        final String sessionToken = authHeader.substring(7);

        if (SecurityContextHolder.getContext().getAuthentication() != null){
            log.debug("User is already authenticated. Skipping session authentication for request: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        try{
            JwtIdentity identity = jwtService.extractHttpIdentity(sessionToken);
            final UUID sessionId = identity.sessionId();
            final UUID userId = identity.userId();

            var session = sessionService.findActiveSessionById(sessionId);
            var user = session.getDevice().getUser();

            if (!session.getDevice().getUser().getId().equals(userId)){
                log.warn("Session ID: {} is not associated with user ID: {}.", sessionId, userId);
                exceptionResolver.resolveException(request,response,null,
                        new UnauthorizedException(AuthMessages.SESSION_NOT_MATCHING_USER));
                return;
            }

            Device currentDevice = deviceService.findDevice(user, DeviceIdentityRequest.from(request))
                    .orElse(null);

            if (currentDevice == null || !session.getDevice().getId().equals(currentDevice.getId())){
                session.setActive(false);
                session.setLastAccessedAt(Instant.now());
                sessionService.save(session);
                log.error("SECURITY ALERT: Session ID: {} is not associated with the current device. Session has been deactivated.", sessionId);
                exceptionResolver.resolveException(request,response,null,
                        new UnauthorizedException(AuthMessages.SESSION_NOT_MATCHING_CURRENT_DEVICE));
                return;
            }


            touchLastAccess(session);

            UserPrincipal principal = UserPrincipal.from(user, session.getId());

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );

            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));



            SecurityContextHolder.getContext().setAuthentication(authToken);
            request.setAttribute(RequestLoggingFilter.USER_ID_ATTRIBUTE, principal.id());
            LogContext.put(LogContext.USER_ID, principal.id());
            LogContext.put(LogContext.SESSION_ID, sessionId);
            LogContext.put(LogContext.DEVICE_ID, currentDevice.getId());
            log.debug("Authenticated user ID: {} with session ID: {}", userId, sessionId);

        }catch (JwtException | ResourceNotFoundException | UnauthorizedException e){
            log.warn("Authentication failed for request {}: {}", request.getRequestURI(), e.getMessage());
            exceptionResolver.resolveException(request,response,null, e);
            return;
        }catch (Exception e){
            log.debug("Failed to authenticate session token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SELF_AUTHENTICATING_PATHS.contains(request.getRequestURI());
    }

    private void touchLastAccess(Session session) {
        Instant now = Instant.now();
        Instant lastAccessedAt = session.getLastAccessedAt();

        if (lastAccessedAt != null && lastAccessedAt.isAfter(now.minus(LAST_ACCESS_WRITE_INTERVAL))) {
            return;
        }

        session.setLastAccessedAt(now);
        sessionService.save(session);
    }
}
