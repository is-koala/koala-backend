package com.exskylab.koala.core.privacy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushTokenMutationLockTest {

    @Test
    void recipientLocksAreDistinctAndGloballyOrdered() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<UUID> acquired = new ArrayList<>();
        PushTokenMutationLock lock = new PushTokenMutationLock() {
            @Override
            public void lockMutations() {
            }

            @Override
            public void lockUser(UUID userId) {
                acquired.add(userId);
            }

            @Override
            public void lockTokens(Collection<String> tokens) {
            }
        };

        lock.lockUsers(java.util.Arrays.asList(second, first, second, null));

        assertEquals(List.of(first, second), acquired);
    }
}
