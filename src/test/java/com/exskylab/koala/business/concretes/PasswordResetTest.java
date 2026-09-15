package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.auth.request.ForgotPasswordDto;
import com.exskylab.koala.core.dtos.auth.request.ResetPasswordDto;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.geoip.GeoIpResolver;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.events.PasswordResetCompletedEvent;
import com.exskylab.koala.core.properties.SessionProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class PasswordResetTest {

    private UserService userService;
    private NotificationService notificationService;
    private SessionService sessionService;
    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;
    private PasswordResetTokenService passwordResetTokenService;
    private AuthLinkService authLinkService;
    private ApplicationEventPublisher eventPublisher;
    private AuthManager authManager;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        notificationService = mock(NotificationService.class);
        sessionService = mock(SessionService.class);
        jwtService = mock(JwtService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        passwordResetTokenService = mock(PasswordResetTokenService.class);
        authLinkService = mock(AuthLinkService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        authManager = new AuthManager(
                userService, mock(PendingRegistrationService.class), notificationService,
                authLinkService,
                jwtService, passwordEncoder, mock(DeviceService.class), sessionService,
                mock(LoginAttemptService.class), mock(SecurityService.class), new SessionProperties(),
                mock(LegalService.class), passwordResetTokenService, eventPublisher,
                GeoIpResolver.unavailable());
    }

    private User user() {
        User user = new User();
        user.setId(userId);
        user.setEmail("ada@example.com");
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");
        user.setPassword("eski-hash");
        return user;
    }

    @Test
    void anUnknownAddressGetsTheSameResponseAndNoMailIsSent() {
        when(userService.getByEmail("yok@example.com")).thenThrow(new ResourceNotFoundException("{user.not.found}"));

        assertDoesNotThrow(() -> authManager.requestPasswordReset(new ForgotPasswordDto("yok@example.com")));

        verify(notificationService, never()).sendEmail(any(), any(), anyBoolean());
    }

    @Test
    void aKnownAddressReceivesAResetLink() {
        when(userService.getByEmail("ada@example.com")).thenReturn(user());
        when(passwordResetTokenService.issue(any(User.class))).thenReturn("reset-token");
        when(authLinkService.passwordResetLandingLink("reset-token"))
                .thenReturn("https://iskoala.com/sifre-sifirla?token=reset-token");

        authManager.requestPasswordReset(new ForgotPasswordDto("ada@example.com"));

        var email = org.mockito.ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notificationService).sendEmail(email.capture(), eq(com.exskylab.koala.entities.DispatchPriority.HIGH), eq(true));
        assertEquals("password-reset-request-template", email.getValue().getTemplateName());
        assertEquals("https://iskoala.com/sifre-sifirla?token=reset-token",
                email.getValue().getTemplateParameters().get("resetLink"));
        assertTrue(email.getValue().getSubject().contains("parola sıfırlama"));
    }

    @Test
    void resettingReplacesTheHashAndClosesEverySession() {
        User user = user();
        when(passwordResetTokenService.consume("reset-token")).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        when(passwordEncoder.encode("YeniParola1")).thenReturn("yeni-hash");

        authManager.resetPassword(new ResetPasswordDto("reset-token", "YeniParola1"));

        assertEquals("yeni-hash", user.getPassword());
        verify(userService).save(user);
        verify(sessionService).invalidateActiveSessionsForUserIdExcludingSessionId(eq(userId), isNull());
        verify(eventPublisher).publishEvent(new PasswordResetCompletedEvent(userId));
    }

    @Test
    void anInvalidTokenIsRejectedBeforeAnythingChanges() {
        when(passwordResetTokenService.consume("gecersiz-token"))
                .thenThrow(new ValidationException("{invalid.token}"));

        org.junit.jupiter.api.Assertions.assertThrows(ValidationException.class,
                () -> authManager.resetPassword(new ResetPasswordDto("gecersiz-token", "YeniParola1")));

        verify(userService, never()).save(any());
    }
}
