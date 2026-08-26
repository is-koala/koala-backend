package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.constants.SessionMessages;
import com.exskylab.koala.core.dtos.session.response.CreatedSessionInfo;
import com.exskylab.koala.core.exceptions.SessionNotFoundException;
import com.exskylab.koala.core.exceptions.UserNotFoundException;
import com.exskylab.koala.core.properties.SessionProperties;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.dataAccess.SessionDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class SessionManager implements SessionService {

    private final SessionDao sessionDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionProperties sessionProperties;
    private Logger logger = LoggerFactory.getLogger(SessionManager.class);

    public SessionManager(SessionDao sessionDao, PasswordEncoder passwordEncoder, JwtService jwtService,
                          SessionProperties sessionProperties) {
        this.sessionDao = sessionDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionProperties = sessionProperties;
    }

    @Override
    @Transactional
    public CreatedSessionInfo createSession(Device device, String ipAddress) {
        logger.info("Creating session for device: {}", device.getId());

        var user = device.getUser();
        if (user == null){
            logger.error("Device with id: {} does not have an associated user.", device.getId());
            throw new UserNotFoundException(SessionMessages.USER_NOT_FOUND_FOR_DEVICE);
        }

        String originalRefreshToken = generateSecureRandomToken();
        String hashedRefreshToken = passwordEncoder.encode(originalRefreshToken);


        Session session = new Session();
        session.setDevice(device);
        session.setIpAddress(ipAddress);
        session.setRefreshTokenHash(hashedRefreshToken);
        session.setRefreshExpiresAt(LocalDateTime.now().plusDays(sessionProperties.getRefreshValidityDays()));
        session.setActive(true);
        session.setLastAccessedAt(LocalDateTime.now());

        Session savedSession = sessionDao.save(session);

        String accessToken = jwtService.generateAccessToken(user, savedSession.getId());

        return new CreatedSessionInfo(savedSession, accessToken, originalRefreshToken);
    }


    @Override
    public Session findActiveSessionById(UUID sessionId) {
        return sessionDao.findByIdAndIsActive(sessionId, true)
                .orElseThrow(() -> new SessionNotFoundException(SessionMessages.SESSION_NOT_FOUND));
    }

    @Override
    public Session save(Session session) {
        logger.info("Saving session with id: {}", session.getId());
        return sessionDao.save(session);
    }

    @Override
    @Transactional
    public void invalidateActiveSessionsForDevice(UUID deviceId) {
        logger.info("Invalidating active sessions for device with id: {}", deviceId);

        var activeSessions = sessionDao.findAllByDeviceIdAndIsActive(deviceId, true);

        for (Session session : activeSessions){
            session.setActive(false);
        }


        sessionDao.saveAll(activeSessions);
        logger.info("Invalidated {} active sessions for device with id: {}", activeSessions.size(), deviceId);
    }

    @Override
    @Transactional
    public void invalidateActiveSessionsForUserIdExcludingSessionId(UUID userId, UUID currentSessionId) {
        logger.info("Invalidating active sessions for user with id: {}, currentSessionId: {}", userId, currentSessionId);

        var activeSessions = sessionDao.findAllByDevice_User_IdAndIsActive(userId, true);

        for (var session : activeSessions){
            if (currentSessionId != null && session.getId().equals(currentSessionId)){
                continue;
            }
            session.setActive(false);
            sessionDao.save(session);
            logger.info("Invalidated session with id: {}", session.getId());
        }

        logger.info("Invalidated {} active sessions for user with id: {}", activeSessions.size(), userId);
    }

    private String generateSecureRandomToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
