package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.user.response.AdminSessionSummaryDto;
import com.exskylab.koala.core.dtos.session.response.CreatedSessionDto;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.Session;

import java.util.List;
import java.util.UUID;

public interface SessionService {
    CreatedSessionDto createSession(Device device, String ipAddress);

    Session findActiveSessionById(UUID sessionId);

    Session save(Session session);

    public void invalidateActiveSessionsForDevice(UUID deviceId);

    void invalidateActiveSessionsForUserIdExcludingSessionId(UUID userId, UUID sessionId);

    List<Session> getMyActiveSessions();

    void revokeSession(UUID sessionId);

    int revokeAllOtherSessions();

    AdminSessionSummaryDto summarizeForStaff(UUID userId);
}
