package com.exskylab.koala.core.privacy;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public interface PushTokenMutationLock {

    void lockMutations();

    void lockUser(UUID userId);

    default void lockUsers(Collection<UUID> userIds) {
        userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(this::lockUser);
    }

    void lockTokens(Collection<String> tokens);
}
