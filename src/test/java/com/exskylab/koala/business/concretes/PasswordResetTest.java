package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.auth.request.ForgotPasswordDto;
import com.exskylab.koala.core.dtos.auth.request.ResetPasswordDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.SessionProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private AuthManager authManager;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        notificationService = mock(NotificationService.class);
        sessionService = mock(SessionService.class);
        jwtService = mock(JwtService.class);
        passwordEncoder = mock(PasswordEncoder.class);

        authManager = new AuthManager(
                userService, mock(PendingRegistrationService.class), notificationService,
                new AppProperties("http://localhost:8080", "http://localhost:3000"),
                jwtService, passwordEncoder, mock(DeviceService.class), sessionService,
                mock(LoginAttemptService.class), mock(SecurityService.class), new SessionProperties(),
                mock(LegalService.class));
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
        when(jwtService.generatePasswordResetToken(userId)).thenReturn("reset-token");

        authManager.requestPasswordReset(new ForgotPasswordDto("ada@example.com"));

        verify(notificationService, times(1)).sendEmail(any(), any(), eq(true));
    }

    @Test
    void resettingReplacesTheHashAndClosesEverySession() {
        User user = user();
        when(jwtService.extractPasswordResetUserId("reset-token")).thenReturn(userId);
        when(userService.getById(userId)).thenReturn(user);
        when(passwordEncoder.encode("YeniParola1")).thenReturn("yeni-hash");

        authManager.resetPassword(new ResetPasswordDto("reset-token", "YeniParola1"));

        assertEquals("yeni-hash", user.getPassword());
        verify(userService).save(user);
        verify(sessionService).invalidateActiveSessionsForUserIdExcludingSessionId(eq(userId), isNull());
    }

    @Test
    void aTokenOfTheWrongKindIsRejectedBeforeAnythingChanges() {
        when(jwtService.extractPasswordResetUserId("yanlis-tur"))
                .thenThrow(new ValidationException("{invalid.token}"));

        org.junit.jupiter.api.Assertions.assertThrows(ValidationException.class,
                () -> authManager.resetPassword(new ResetPasswordDto("yanlis-tur", "YeniParola1")));

        verify(userService, never()).save(any());
    }
}
