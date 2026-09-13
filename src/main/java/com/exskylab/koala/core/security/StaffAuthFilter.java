package com.exskylab.koala.core.security;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.exceptions.UnauthorizedException;
import com.exskylab.koala.core.logging.LogContext;
import com.exskylab.koala.core.configs.RequestLoggingFilter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Optional;

@Component
@Slf4j
public class StaffAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final StaffAccountService staffAccountService;
    private final HandlerExceptionResolver exceptionResolver;

    public StaffAuthFilter(JwtService jwtService, StaffAccountService staffAccountService,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        this.staffAccountService = staffAccountService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER.length());

        if (!jwtService.looksLikeAStaffToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            StaffJwtIdentity identity = jwtService.extractStaffIdentity(token, JwtService.STAFF_ACCESS_AUDIENCE);
            Optional<StaffPrincipal> principal =
                    staffAccountService.principalOf(identity.staffId(), identity.tokenVersion());

            if (principal.isEmpty()) {
                log.warn("Staff token is valid but the session is no longer recognized: staffId={}", identity.staffId());
                exceptionResolver.resolveException(request, response, null,
                        new UnauthorizedException(StaffMessages.SESSION_ENDED));
                return;
            }

            StaffPrincipal staff = principal.get();
            SecurityContextHolder.getContext().setAuthentication(new StaffAuthenticationToken(token, staff));
            request.setAttribute(RequestLoggingFilter.USER_ID_ATTRIBUTE, staff.staffId());
            LogContext.put(LogContext.USER_ID, staff.staffId());

        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Staff token could not be verified: {}", exception.getMessage());
            exceptionResolver.resolveException(request, response, null,
                    new UnauthorizedException(StaffMessages.TOKEN_INVALID));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
