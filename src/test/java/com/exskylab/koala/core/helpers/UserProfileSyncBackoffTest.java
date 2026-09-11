package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.UserProfileSyncProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserProfileSyncBackoffTest {

    @Test
    void exponentialBackoffUsesBoundedJitterWithoutCrossingTheCap() {
        UserProfileSyncProperties properties = new UserProfileSyncProperties();
        properties.setInitialBackoff(Duration.ofMinutes(1));
        properties.setMaxBackoff(Duration.ofMinutes(3));
        properties.setJitterFactor(0.5);

        UserProfileSyncBackoff minimum = new UserProfileSyncBackoff(properties, () -> 0.0);
        UserProfileSyncBackoff maximum = new UserProfileSyncBackoff(properties, () -> 1.0);

        assertEquals(Duration.ofSeconds(30), minimum.delayFor(1));
        assertEquals(Duration.ofMinutes(1), maximum.delayFor(1));
        assertEquals(Duration.ofSeconds(90), minimum.delayFor(3));
        assertEquals(Duration.ofMinutes(3), maximum.delayFor(20));
    }
}
