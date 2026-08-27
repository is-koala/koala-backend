package com.exskylab.koala.business.abstracts;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PresenceService {

    void markOnline(UUID userId, String sessionId);

    void markOffline(UUID userId, String sessionId);

    boolean isOnline(UUID userId);

    Instant lastSeenAt(UUID userId);

    Map<UUID, Boolean> statusOf(Collection<UUID> userIds);
}
