package com.exskylab.koala.core.logging;

import com.exskylab.koala.entities.Notification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationDispatchLogContextTest {

    @AfterEach
    void clearMdc() {
        LogContext.clear();
    }

    @Test
    void theOriginatingRequestContextIsCapturedAndRestoredOnlyForItsOwnPublish() {
        Notification notification = new Notification();
        Map<String, String> origin = Map.of(
                LogContext.REQUEST_ID, "origin-request",
                LogContext.USER_ID, "origin-user",
                LogContext.SESSION_ID, "origin-session",
                LogContext.DEVICE_ID, "origin-device");
        LogContext.adopt(origin);
        NotificationDispatchLogContext.capture(notification);
        LogContext.adopt(Map.of(LogContext.REQUEST_ID, "scheduler-request"));

        try (var ignored = NotificationDispatchLogContext.activate(notification)) {
            assertEquals(origin, LogContext.carried());
        }

        assertEquals("scheduler-request", MDC.get(LogContext.REQUEST_ID));
        assertNull(MDC.get(LogContext.USER_ID));
    }

    @Test
    void legacyRowsWithoutAnOriginNeverShareTheSchedulersBatchRequestId() {
        LogContext.adopt(Map.of(LogContext.REQUEST_ID, "scheduler-request"));
        Notification legacy = new Notification();
        String first;
        String second;

        try (var ignored = NotificationDispatchLogContext.activate(legacy)) {
            first = MDC.get(LogContext.REQUEST_ID);
        }
        try (var ignored = NotificationDispatchLogContext.activate(legacy)) {
            second = MDC.get(LogContext.REQUEST_ID);
        }

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals("scheduler-request", first);
        assertNotEquals(first, second);
    }
}
