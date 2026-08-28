package com.exskylab.koala.business.concretes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PresenceRulesTest {

    private PresenceManager presence;
    private final UUID user = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        presence = new PresenceManager();
    }

    @Test
    void aUserWhoNeverConnectedIsOffline() {
        assertFalse(presence.isOnline(user));
        assertNull(presence.lastSeenAt(user));
    }

    @Test
    void connectingMarksTheUserOnline() {
        presence.markOnline(user, "sess-1");

        assertTrue(presence.isOnline(user));
        assertNotNull(presence.lastSeenAt(user));
    }

    @Test
    void closingOneOfTwoDevicesKeepsTheUserOnline() {
        presence.markOnline(user, "phone");
        presence.markOnline(user, "tablet");

        presence.markOffline(user, "phone");

        assertTrue(presence.isOnline(user), "ikinci cihaz hâlâ bağlı");
    }

    @Test
    void closingTheLastConnectionMarksTheUserOffline() {
        presence.markOnline(user, "phone");
        presence.markOffline(user, "phone");

        assertFalse(presence.isOnline(user));
        assertNotNull(presence.lastSeenAt(user), "son görülme zamanı korunur");
    }

    @Test
    void closingAConnectionThatWasNeverOpenIsHarmless() {
        assertDoesNotThrow(() -> presence.markOffline(user, "ghost"));
        assertFalse(presence.isOnline(user));
    }

    @Test
    void connectingTwiceWithTheSameSessionCountsOnce() {
        presence.markOnline(user, "phone");
        presence.markOnline(user, "phone");
        presence.markOffline(user, "phone");

        assertFalse(presence.isOnline(user));
    }

    @Test
    void aBulkLookupAnswersForEveryRequestedUser() {
        presence.markOnline(user, "phone");

        var statuses = presence.statusOf(List.of(user, other));

        assertEquals(2, statuses.size());
        assertTrue(statuses.get(user));
        assertFalse(statuses.get(other));
    }

    @Test
    void aBulkLookupToleratesRepeatedIds() {
        assertDoesNotThrow(() -> presence.statusOf(List.of(user, user, other)));
    }

    @Test
    void presenceIsScopedPerUser() {
        presence.markOnline(user, "phone");

        assertTrue(presence.isOnline(user));
        assertFalse(presence.isOnline(other));
    }
}
