package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuthLinkService;
import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.LoginAttemptService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.PasswordResetTokenService;
import com.exskylab.koala.business.abstracts.PendingRegistrationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.auth.request.AuthLoginDto;
import com.exskylab.koala.core.geoip.GeoIpResolver;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.UnauthorizedException;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.properties.SessionProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.entities.LoginAttempt;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginConcealmentTest {

    private static final String UNREGISTERED_EMAIL = "kayitsiz@example.com";
    private static final String REGISTERED_EMAIL = "kayitli@example.com";
    private static final String GIVEN_PASSWORD = "yanlis-parola";
    private static final String STORED_HASH = "$2a$10$gercek.kullanicinin.hashi";
    private static final String ABSENT_ACCOUNT_HASH = "$2a$10$olmayan.hesabin.hashi";

    private UserService userService;
    private PasswordEncoder passwordEncoder;
    private LoginAttemptService loginAttemptService;
    private DeviceService deviceService;
    private AuthManager authManager;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        loginAttemptService = mock(LoginAttemptService.class);
        deviceService = mock(DeviceService.class);

        when(passwordEncoder.encode(anyString())).thenReturn(ABSENT_ACCOUNT_HASH);
        when(userService.getReference(any())).thenAnswer(invocation -> {
            User phantom = new User();
            phantom.setId(invocation.getArgument(0));
            return phantom;
        });

        authManager = new AuthManager(
                userService, mock(PendingRegistrationService.class), mock(NotificationService.class),
                mock(AuthLinkService.class), mock(JwtService.class), passwordEncoder,
                deviceService, mock(SessionService.class), loginAttemptService,
                mock(SecurityService.class), new SessionProperties(), mock(LegalService.class),
                mock(PasswordResetTokenService.class), mock(ApplicationEventPublisher.class),
                GeoIpResolver.unavailable());

        when(userService.getByEmail(UNREGISTERED_EMAIL))
                .thenThrow(new ResourceNotFoundException("{user.not.found.by.email}"));
        when(userService.getByEmail(REGISTERED_EMAIL)).thenReturn(registeredUser());
    }

    @Test
    void anUnregisteredEmailIsRefusedExactlyLikeARegisteredOneWithAWrongPassword() {
        UnauthorizedException unregistered = assertThrows(UnauthorizedException.class,
                () -> authManager.login(loginWith(UNREGISTERED_EMAIL), client()));
        UnauthorizedException wrongPassword = assertThrows(UnauthorizedException.class,
                () -> authManager.login(loginWith(REGISTERED_EMAIL), client()));

        assertEquals(wrongPassword.getHttpStatus(), unregistered.getHttpStatus());
        assertEquals(wrongPassword.getErrorCode(), unregistered.getErrorCode());
        assertEquals(wrongPassword.getMessage(), unregistered.getMessage());
    }

    @Test
    void anUnregisteredEmailStillPaysForAPasswordVerification() {
        assertThrows(RuntimeException.class, () -> authManager.login(loginWith(UNREGISTERED_EMAIL), client()));

        verify(passwordEncoder).matches(GIVEN_PASSWORD, ABSENT_ACCOUNT_HASH);
    }

    @Test
    void theAbsentAccountHashComesFromTheConfiguredEncoder() {
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    void anUnregisteredEmailIsStillRecordedAsAFailedLoginAttempt() {
        assertThrows(RuntimeException.class, () -> authManager.login(loginWith(UNREGISTERED_EMAIL), client()));

        ArgumentCaptor<LoginAttempt> saved = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptService).save(saved.capture());

        assertEquals(UNREGISTERED_EMAIL, saved.getValue().getEmailAttempted());
        assertFalse(saved.getValue().isSuccess());
        assertNull(saved.getValue().getUser());
    }

    @Test
    void anUnregisteredEmailStillPaysForTheDeviceLookupTheRegisteredOnePays() {
        assertThrows(RuntimeException.class, () -> authManager.login(loginWith(UNREGISTERED_EMAIL), client()));
        assertThrows(RuntimeException.class, () -> authManager.login(loginWith(REGISTERED_EMAIL), client()));

        verify(deviceService, times(2)).findDevice(any(), eq("Koala JUnit"), eq("cihaz-1"), any(), any());
    }

    private User registeredUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(REGISTERED_EMAIL);
        user.setPassword(STORED_HASH);
        return user;
    }

    private AuthLoginDto loginWith(String email) {
        return new AuthLoginDto(email, GIVEN_PASSWORD);
    }

    private ClientContext client() {
        return new ClientContext("203.0.113.7", "Koala JUnit", "cihaz-1");
    }
}
