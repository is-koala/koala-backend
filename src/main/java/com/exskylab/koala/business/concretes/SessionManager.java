package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.constants.SessionMessages;
import com.exskylab.koala.core.dtos.session.response.CreatedSessionDto;
import com.exskylab.koala.core.dtos.user.response.AdminSessionSummaryDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.properties.SessionProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.dataAccess.SessionDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.Session;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.exskylab.koala.core.constants.MyWorkMessages;
import java.util.List;
import com.exskylab.koala.business.abstracts.SecurityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
public class SessionManager implements SessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SessionDao sessionDao;
    private final SecurityService securityService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionProperties sessionProperties;

    public SessionManager(SessionDao sessionDao, PasswordEncoder passwordEncoder, JwtService jwtService,
                          SessionProperties sessionProperties, SecurityService securityService) {
        this.sessionDao = sessionDao;
        this.securityService = securityService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionProperties = sessionProperties;
    }

    @Override
    @Transactional
    public CreatedSessionDto createSession(Device device, String ipAddress) {
        log.info("Creating session for device: {}", device.getId());

        var user = device.getUser();
        if (user == null){
            log.error("Device with id: {} does not have an associated user.", device.getId());
            throw new ResourceNotFoundException(SessionMessages.USER_NOT_FOUND_FOR_DEVICE);
        }

        String originalRefreshToken = generateSecureRandomToken();
        String hashedRefreshToken = passwordEncoder.encode(originalRefreshToken);


        Session session = new Session();
        session.setDevice(device);
        session.setIpAddress(ipAddress);
        session.setRefreshTokenHash(hashedRefreshToken);
        session.setRefreshExpiresAt(Instant.now().plus(sessionProperties.getRefreshValidityDays(), ChronoUnit.DAYS));
        session.setActive(true);
        session.setLastAccessedAt(Instant.now());

        Session savedSession = sessionDao.save(session);

        String accessToken = jwtService.generateAccessToken(user, savedSession.getId());

        return new CreatedSessionDto(savedSession, accessToken, originalRefreshToken);
    }


    @Override
    public Session findActiveSessionById(UUID sessionId) {
        return sessionDao.findByIdAndIsActive(sessionId, true)
                .orElseThrow(() -> new ResourceNotFoundException(SessionMessages.SESSION_NOT_FOUND));
    }

    @Override
    public Session save(Session session) {
        log.info("Saving session with id: {}", session.getId());
        return sessionDao.save(session);
    }

    @Override
    @Transactional
    public void invalidateActiveSessionsForDevice(UUID deviceId) {
        log.info("Invalidating active sessions for device with id: {}", deviceId);

        var activeSessions = sessionDao.findAllByDeviceIdAndIsActive(deviceId, true);

        for (Session session : activeSessions){
            session.setActive(false);
        }


        sessionDao.saveAll(activeSessions);
        log.info("Invalidated {} active sessions for device with id: {}", activeSessions.size(), deviceId);
    }

    @Override
    @Transactional
    public void invalidateActiveSessionsForUserIdExcludingSessionId(UUID userId, UUID currentSessionId) {
        log.info("Invalidating active sessions for user with id: {}, currentSessionId: {}", userId, currentSessionId);

        var activeSessions = sessionDao.findAllByDevice_User_IdAndIsActive(userId, true);

        for (var session : activeSessions){
            if (currentSessionId != null && session.getId().equals(currentSessionId)){
                continue;
            }
            session.setActive(false);
            sessionDao.save(session);
            log.info("Invalidated session with id: {}", session.getId());
        }

        log.info("Invalidated {} active sessions for user with id: {}", activeSessions.size(), userId);
    }

    private String generateSecureRandomToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Session> getMyActiveSessions() {
        return sessionDao.findAllByDevice_User_IdAndIsActive(securityService.getAuthenticatedUserId(), true);
    }

    @Override
    @Transactional
    public void revokeSession(UUID sessionId) {
        Session session = sessionDao.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(MyWorkMessages.SESSION_NOT_FOUND));

        if (!session.getDevice().getUser().getId().equals(securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(MyWorkMessages.SESSION_NOT_FOUND);
        }

        session.setActive(false);
        sessionDao.save(session);
        log.info("Session {} revoked by its owner", sessionId);
    }

    @Override
    @Transactional
    public int revokeAllOtherSessions() {
        UUID currentSessionId = securityService.getAuthenticatedPrincipal().sessionId();
        UUID userId = securityService.getAuthenticatedUserId();

        List<Session> others = sessionDao.findAllByDevice_User_IdAndIsActive(userId, true).stream()
                .filter(session -> !session.getId().equals(currentSessionId))
                .toList();

        others.forEach(session -> session.setActive(false));
        sessionDao.saveAll(others);

        log.info("{} other sessions revoked for user {}", others.size(), userId);
        return others.size();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSessionSummaryDto summarizeForStaff(UUID userId) {
        long active = sessionDao.countByDevice_User_IdAndIsActive(userId, true);
        Session latest = sessionDao.findFirstByDevice_User_IdOrderByCreatedAtDesc(userId).orElse(null);
        return new AdminSessionSummaryDto(active,
                latest == null ? null : latest.getCreatedAt(),
                latest == null || latest.getDevice() == null ? null : latest.getDevice().getType());
    }
}
