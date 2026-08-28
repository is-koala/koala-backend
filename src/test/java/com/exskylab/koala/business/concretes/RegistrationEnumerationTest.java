package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.LoginAttemptService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.PendingRegistrationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.auth.request.AuthStartRegistrationDto;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.SessionProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.entities.PendingRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationEnumerationTest {

    private UserService userService;
    private PendingRegistrationService pendingRegistrationService;
    private NotificationService notificationService;
    private AuthManager authManager;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        pendingRegistrationService = mock(PendingRegistrationService.class);
        notificationService = mock(NotificationService.class);

        AppProperties appProperties = new AppProperties("http://localhost:8080", "http://localhost:3000");

        authManager = new AuthManager(
                userService,
                pendingRegistrationService,
                notificationService,
                appProperties,
                mock(JwtService.class),
                mock(PasswordEncoder.class),
                mock(DeviceService.class),
                mock(SessionService.class),
                mock(LoginAttemptService.class),
                mock(SecurityService.class),
                new SessionProperties(),
                mock(LegalService.class));

        when(pendingRegistrationService.createPendingRegistration(any())).thenReturn(pendingRegistration());
    }

    @Test
    void anAlreadyRegisteredAddressDoesNotProduceADistinguishableError() {
        when(userService.existsByEmail("kayitli@example.com")).thenReturn(true);
        when(pendingRegistrationService.findByEmail("kayitli@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authManager.startRegistration(request("kayitli@example.com")));

        verify(pendingRegistrationService, never()).createPendingRegistration(any());
        verify(notificationService, never()).sendEmail(any(), any(), anyBoolean());
    }

    @Test
    void aNewAddressStartsRegistration() {
        when(userService.existsByEmail("yeni@example.com")).thenReturn(false);
        when(pendingRegistrationService.findByEmail("yeni@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authManager.startRegistration(request("yeni@example.com")));

        verify(pendingRegistrationService, times(1)).createPendingRegistration(any());
        verify(notificationService, times(1)).sendEmail(any(), any(), eq(true));
    }

    @Test
    void aSecondAttemptResendsInsteadOfFailing() {
        PendingRegistration existing = pendingRegistration();
        when(userService.existsByEmail("bekleyen@example.com")).thenReturn(false);
        when(pendingRegistrationService.findByEmail("bekleyen@example.com")).thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> authManager.startRegistration(request("bekleyen@example.com")));

        verify(pendingRegistrationService).deleteById(existing.getId());
        verify(pendingRegistrationService, times(1)).createPendingRegistration(any());
        verify(notificationService, times(1)).sendEmail(any(), any(), eq(true));
    }

    private AuthStartRegistrationDto request(String email) {
        AuthStartRegistrationDto dto = new AuthStartRegistrationDto();
        dto.setEmail(email);
        dto.setFirstName("Ada");
        dto.setLastName("Yilmaz");
        return dto;
    }

    private PendingRegistration pendingRegistration() {
        PendingRegistration pending = new PendingRegistration();
        pending.setId(UUID.randomUUID());
        pending.setEmail("x@example.com");
        pending.setFirstName("Ada");
        pending.setLastName("Yilmaz");
        pending.setToken(UUID.randomUUID());
        pending.setExpiresAt(Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS));
        return pending;
    }
}
