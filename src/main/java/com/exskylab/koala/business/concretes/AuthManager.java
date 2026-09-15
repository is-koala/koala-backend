package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.helpers.LogMasks;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.properties.SessionProperties;
import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.dtos.auth.request.*;
import com.exskylab.koala.core.dtos.auth.response.AuthSetPasswordDto;
import com.exskylab.koala.core.dtos.auth.response.TokenResponseDto;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.dtos.session.response.CreatedSessionDto;
import com.exskylab.koala.core.exceptions.*;
import com.exskylab.koala.core.events.PasswordResetCompletedEvent;
import com.exskylab.koala.core.geoip.GeoIpResolver;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.entities.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;


@Service
@Slf4j
public class AuthManager implements AuthService {

    private final AuthLinkService authLinkService;
    private final UserService userService;
    private final PendingRegistrationService pendingRegistrationService;
    private final NotificationService notificationService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final DeviceService deviceService;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttemptService;
    private final SecurityService securityService;
    private final SessionProperties sessionProperties;
    private final LegalService legalService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final GeoIpResolver geoIpResolver;
    private final String absentAccountPasswordHash;

    public AuthManager(UserService userService,
                       PendingRegistrationService pendingRegistrationService,
                       NotificationService notificationService,
                       AuthLinkService authLinkService,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       DeviceService deviceService,
                       SessionService sessionService,
                       LoginAttemptService loginAttemptService,
                       SecurityService securityService,
                       SessionProperties sessionProperties,
                       LegalService legalService,
                       PasswordResetTokenService passwordResetTokenService,
                       ApplicationEventPublisher eventPublisher,
                       GeoIpResolver geoIpResolver) {
        this.userService = userService;
        this.pendingRegistrationService = pendingRegistrationService;
        this.notificationService = notificationService;
        this.authLinkService = authLinkService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.deviceService = deviceService;
        this.sessionService = sessionService;
        this.loginAttemptService = loginAttemptService;
        this.securityService = securityService;
        this.sessionProperties = sessionProperties;
        this.legalService = legalService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.eventPublisher = eventPublisher;
        this.geoIpResolver = geoIpResolver;
        this.absentAccountPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public void startRegistration(AuthStartRegistrationDto authStartRegistrationDto) {
        log.debug("Starting registration");

        if (userService.existsByEmail(authStartRegistrationDto.getEmail())){
            log.info("Registration requested for an address that already has an account. Responding without disclosing it.");
            return;
        }

        pendingRegistrationService.findByEmail(authStartRegistrationDto.getEmail())
                .ifPresent(existing -> {
                    log.info("Replacing the existing pending registration so the verification mail can be resent.");
                    pendingRegistrationService.deleteById(existing.getId());
                });

        PendingRegistration pendingRegistration =
                pendingRegistrationService.createPendingRegistration(authStartRegistrationDto);
        log.info("Pending registration created with id: {}", pendingRegistration.getId());

        String verificationLink = authLinkService.registrationLandingLink(
                pendingRegistration.getToken().toString());

        Map<String, Object> emailParams = Map.of(
                "firstName", authStartRegistrationDto.getFirstName(),
                "lastName", authStartRegistrationDto.getLastName(),
                "verificationLink", verificationLink
        );

        SendEmailDto sendEmailDto = new SendEmailDto();
        sendEmailDto.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        sendEmailDto.setDestinationEmail(authStartRegistrationDto.getEmail());
        sendEmailDto.setTemplateName("registration-verification-template");
        sendEmailDto.setTemplateParameters(emailParams);
        sendEmailDto.setSubject("Koala kayıt işlemi için e-posta doğrulaması");

        notificationService.sendEmail(sendEmailDto, DispatchPriority.HIGH, true);
        log.info("Verification email queued for pending registration id: {}", pendingRegistration.getId());

        log.info("Registration process started successfully for pending registration id: {}", pendingRegistration.getId());
    }

    @Override
    public AuthSetPasswordDto verifyRegistrationToken(AuthVerifyRegistrationTokenDto authVerifyRegistrationTokenDto) {
        log.debug("Verifying a registration token.");

        PendingRegistration pendingRegistration;
        try {
            pendingRegistration = pendingRegistrationService.getByToken(authVerifyRegistrationTokenDto.getRegistrationToken());
        } catch (ResourceNotFoundException exception) {
            throw new ValidationException(exception.getMessage());
        }

        if (pendingRegistration.getExpiresAt().isBefore(Instant.now())){
            pendingRegistrationService.deleteById(pendingRegistration.getId());
            log.warn("Registration token expired for pending registration id: {}", pendingRegistration.getId());
            throw new ValidationException(AuthMessages.REGISTRATION_TOKEN_EXPIRED);
        }

        String passwordSetToken = jwtService.generatePasswordSetToken(pendingRegistration.getId());
        log.info("Token verified successfully for pending registration id: {}", pendingRegistration.getId());

        return new AuthSetPasswordDto(passwordSetToken, pendingRegistration.isEmployer());
    }

    @Override
    @Transactional
    public TokenResponseDto completeRegistration(AuthCompleteRegistrationDto authCompleteRegistrationDto,
                                                 ClientContext client) {
        legalService.assertMandatoryConsentsGranted(authCompleteRegistrationDto.getConsents());

        UUID pendingRegistrationId = jwtService.extractPendingRegistrationId(authCompleteRegistrationDto.getSetPasswordToken());

        PendingRegistration pendingRegistration = pendingRegistrationService.getById(pendingRegistrationId);

        log.info("Completing registration for pending registration id: {}", pendingRegistration.getId());


        User newUser = new User();
        newUser.setFirstName(pendingRegistration.getFirstName());
        newUser.setLastName(pendingRegistration.getLastName());
        newUser.setEmail(pendingRegistration.getEmail());
        newUser.setEmployer(pendingRegistration.isEmployer());
        newUser.setPassword(passwordEncoder.encode(authCompleteRegistrationDto.getPassword()));
        newUser.setAuthorities(Set.of(Role.ROLE_USER));

        newUser.setEmailVerified(true);
        newUser.setPhoneVerified(false);
        newUser.setIdentityVerified(false);
        newUser.setCreatedBy(securityService.getSystemUser());

        User savedUser = userService.save(newUser);

        log.info("User created successfully with ID: {}", savedUser.getId());

        legalService.recordConsents(savedUser, authCompleteRegistrationDto.getConsents(), client);

        pendingRegistrationService.deleteById(pendingRegistrationId);

        Device device = deviceService.findOrCreateDevice(savedUser, client.deviceIdentity());

        log.info("Device created or found for user ID: {}", savedUser.getId());

        CreatedSessionDto createdSessionInfo = sessionService.createSession(device, client.ipAddress());
        log.info("Session created for user ID: {}, session ID: {}", savedUser.getId(), createdSessionInfo.session().getId());

        loginAttemptService.save(successfulAttempt(savedUser, device, client));

        sendWelcomeEmail(savedUser);

        return new TokenResponseDto(
                createdSessionInfo.originalToken(),
                createdSessionInfo.originalRefreshToken(),
                createdSessionInfo.session().getRefreshExpiresAt()
        );

    }

    @Override
    public TokenResponseDto login(AuthLoginDto authLoginDto, ClientContext client) {
        client.requireWebDeviceId();
        log.info("Login attempt for {}", LogMasks.email(authLoginDto.getEmail()));

        User user;
        try {
            user = userService.getByEmail(authLoginDto.getEmail());
        } catch (ResourceNotFoundException exception) {
            log.warn("Concealed as bad credentials: no account for {}", LogMasks.email(authLoginDto.getEmail()));
            user = null;
        }

        boolean passwordMatches = passwordEncoder.matches(authLoginDto.getPassword(),
                user == null ? absentAccountPasswordHash : user.getPassword());

        if (user == null || !passwordMatches){
            LoginAttempt loginAttempt = new LoginAttempt();
            loginAttempt.setEmailAttempted(authLoginDto.getEmail());
            loginAttempt.setSuccess(false);
            loginAttempt.setFailureReason(user == null ? "No account for email" : "Invalid email or password");
            loginAttempt.setIpAddress(client.ipAddress());
            loginAttempt.setLocation(geoIpResolver.locate(client.ipAddress()).orElse(null));
            loginAttempt.setDeviceName(client.userAgent());
            loginAttempt.setUser(user);
            loginAttempt.setDevice(knownDevice(user, client));
            loginAttemptService.save(loginAttempt);

            throw new UnauthorizedException(AuthMessages.EMAIL_OR_PASSWORD_MISMATCH);
        }

        Device device = deviceService.findOrCreateDevice(user, client.deviceIdentity());

        log.info("Device found or created for user ID: {}", user.getId());

        sessionService.invalidateActiveSessionsForDevice(device.getId());
        log.info("Invalidated all active sessions for device ID: {}", device.getId());

        loginAttemptService.save(successfulAttempt(user, device, client));

        CreatedSessionDto createdSessionInfo = sessionService.createSession(device, client.ipAddress());


        log.info("Session created for user ID: {}, session ID: {}", user.getId(), createdSessionInfo.session().getId());
        return new TokenResponseDto(
                createdSessionInfo.originalToken(),
                createdSessionInfo.originalRefreshToken(),
                createdSessionInfo.session().getRefreshExpiresAt()
        );

    }

    @Override
    public TokenResponseDto refreshToken(RefreshTokenDto refreshTokenDto, UUID sessionId, ClientContext client) {
        client.requireWebDeviceId();
        log.info("Refreshing token for session ID: {}", sessionId);
        Session session;
        try {
            session = sessionService.findActiveSessionById(sessionId);
        } catch (ResourceNotFoundException exception) {
            throw new UnauthorizedException(AuthMessages.SESSION_NOT_FOUND_OR_INACTIVE);
        }

        if (!passwordEncoder.matches(refreshTokenDto.getRefreshToken(), session.getRefreshTokenHash())){
            log.error("Refresh token does not match for session ID: {}", sessionId);
            throw new UnauthorizedException(AuthMessages.REFRESH_TOKEN_AND_SESSION_MISMATCH);
        }

        Instant now = Instant.now();

        if (session.getRefreshExpiresAt().isBefore(now)){
            log.error("Refresh token has expired for session ID: {}", sessionId);
            session.setActive(false);
            sessionService.save(session);
            throw new UnauthorizedException(AuthMessages.REFRESH_TOKEN_EXPIRED);
        }

        Device currentDevice = deviceService.findDevice(session.getDevice().getUser(), client.deviceIdentity())
                .orElse(null);

        if (currentDevice == null || !session.getDevice().getId().equals(currentDevice.getId())){
            session.setActive(false);
            session.setLastAccessedAt(now);
            sessionService.save(session);
            log.error("SECURITY ALERT: Session ID: {} is not associated with the current device. Session has been deactivated.", sessionId);
            throw new UnauthorizedException(AuthMessages.SESSION_NOT_MATCHING_CURRENT_DEVICE);
        }


        Instant currentRefreshExpiresAt = session.getRefreshExpiresAt();


        if (currentRefreshExpiresAt.isBefore(now.plus(sessionProperties.getRefreshSlidingWindowDays(), ChronoUnit.DAYS))){
            session.setRefreshExpiresAt(now.plus(sessionProperties.getRefreshValidityDays(), ChronoUnit.DAYS));
            log.info("Refresh token expiry extended for session ID: {}", sessionId);
        }

        session.setLastAccessedAt(now);
        session.getDevice().setLastSeenAt(now);
        if (!Objects.equals(client.ipAddress(), session.getIpAddress())) {
            session.setIpAddress(client.ipAddress());
            session.setLocation(geoIpResolver.locate(client.ipAddress()).orElse(null));
        }
        sessionService.save(session);

        String newAccessToken = jwtService.generateAccessToken(session.getDevice().getUser(), session.getId());
        log.info("Access token refreshed successfully for session ID: {}", sessionId);
        return new TokenResponseDto(newAccessToken, refreshTokenDto.getRefreshToken(), session.getRefreshExpiresAt());
    }

    private LoginAttempt successfulAttempt(User user, Device device, ClientContext client) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUser(user);
        attempt.setEmailAttempted(user.getEmail());
        attempt.setSuccess(true);
        attempt.setIpAddress(client.ipAddress());
        attempt.setLocation(geoIpResolver.locate(client.ipAddress()).orElse(null));
        attempt.setDeviceName(device.getName());
        attempt.setDevice(device);
        return attempt;
    }

    private Device knownDevice(User user, ClientContext client) {
        User subject = user == null ? userService.getReference(UUID.randomUUID()) : user;
        return deviceService.findDevice(subject, client.deviceIdentity()).orElse(null);
    }

    @Override
    public void logout(UUID sessionId) {
        log.info("Logging out session ID: {}", sessionId);
        var session = sessionService.findActiveSessionById(sessionId);
        if (session == null) {
            log.warn("Session with ID: {} not found or already inactive", sessionId);
            throw new ResourceNotFoundException(AuthMessages.SESSION_NOT_FOUND_OR_INACTIVE);
        }
        session.setActive(false);
        session.getDevice().setLastSeenAt(Instant.now());

        sessionService.save(session);
        log.info("Session ID: {} logged out successfully", sessionId);
    }

    @Override
    @Transactional
    public void requestPasswordReset(ForgotPasswordDto forgotPasswordDto) {
        log.info("Password reset requested.");

        User user;
        try {
            user = userService.getByEmail(forgotPasswordDto.getEmail());
        } catch (ResourceNotFoundException exception) {
            log.info("Password reset requested for an unknown address. Responding without disclosing it.");
            return;
        }

        String resetToken = passwordResetTokenService.issue(user);
        String resetLink = authLinkService.passwordResetLandingLink(resetToken);

        SendEmailDto sendEmailDto = new SendEmailDto();
        sendEmailDto.setRecipient(user);
        sendEmailDto.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        sendEmailDto.setDestinationEmail(user.getEmail());
        sendEmailDto.setSubject("Koala parola sıfırlama");
        sendEmailDto.setTemplateName("password-reset-request-template");
        sendEmailDto.setTemplateParameters(Map.of(
                "firstName", user.getFirstName() == null ? "" : user.getFirstName(),
                "lastName", user.getLastName() == null ? "" : user.getLastName(),
                "resetLink", resetLink));

        notificationService.sendEmail(sendEmailDto, DispatchPriority.HIGH, true);
        log.info("Password reset mail queued for user id: {}", user.getId());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordDto resetPasswordDto) {
        UUID userId = passwordResetTokenService.consume(resetPasswordDto.getResetToken());

        User user = userService.getById(userId);
        user.setPassword(passwordEncoder.encode(resetPasswordDto.getNewPassword()));
        userService.save(user);

        sessionService.invalidateActiveSessionsForUserIdExcludingSessionId(userId, null);
        eventPublisher.publishEvent(new PasswordResetCompletedEvent(userId));
        log.info("Password reset completed for user id: {}, all sessions invalidated.", userId);
    }

    private void sendWelcomeEmail(User savedUser) {
        log.debug("Sending welcome email to user with ID: {}", savedUser.getId());

            SendEmailDto sendEmailDto = new SendEmailDto();
            sendEmailDto.setRecipient(savedUser);
            sendEmailDto.setCategory(NotificationCategory.ACCOUNT_SECURITY);
            sendEmailDto.setDestinationEmail(savedUser.getEmail());
            sendEmailDto.setSubject("Koala'ya Hoş Geldin "+ savedUser.getFirstName()+ "!");
            sendEmailDto.setTemplateName("welcome-email-template");
            sendEmailDto.setTemplateParameters(Map.of(
                    "firstName", savedUser.getFirstName(),
                    "lastName", savedUser.getLastName(),
                    "email", savedUser.getEmail()));

            notificationService.sendEmail(sendEmailDto, DispatchPriority.NORMAL, true);
    }

}
