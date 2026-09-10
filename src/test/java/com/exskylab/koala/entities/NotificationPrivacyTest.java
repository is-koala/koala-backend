package com.exskylab.koala.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationPrivacyTest {

    @Test
    void pushLifecycleAlwaysRemovesRawTokenSnapshots() {
        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.PUSH);
        notification.setDestinationPushToken("raw-fcm-token");

        notification.removePushTokenSnapshot();

        assertNull(notification.getDestinationPushToken());
    }

    @Test
    void nonPushLegacyDestinationsAreUnaffected() {
        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setDestinationPushToken("legacy-value");

        notification.removePushTokenSnapshot();

        assertEquals("legacy-value", notification.getDestinationPushToken());
    }
}
