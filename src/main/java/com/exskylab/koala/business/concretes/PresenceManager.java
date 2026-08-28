package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.PresenceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PresenceManager implements PresenceService {

    private final Map<UUID, Set<String>> connectionsByUser = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastSeenByUser = new ConcurrentHashMap<>();

    @Override
    public void markOnline(UUID userId, String sessionId) {
        connectionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
        lastSeenByUser.put(userId, Instant.now());
        log.debug("User {} online with {} connections", userId, connectionsByUser.get(userId).size());
    }

    @Override
    public void markOffline(UUID userId, String sessionId) {
        connectionsByUser.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
        lastSeenByUser.put(userId, Instant.now());
        log.debug("User {} connection closed", userId);
    }

    @Override
    public boolean isOnline(UUID userId) {
        return connectionsByUser.containsKey(userId);
    }

    @Override
    public Instant lastSeenAt(UUID userId) {
        return lastSeenByUser.get(userId);
    }

    @Override
    public Map<UUID, Boolean> statusOf(Collection<UUID> userIds) {
        return userIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), this::isOnline));
    }
}
