package com.exskylab.koala.core.logging;

import com.exskylab.koala.entities.UserProfileSyncOutbox;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

public final class UserProfileSyncLogContext {

    private UserProfileSyncLogContext() {
    }

    public static void capture(UserProfileSyncOutbox outbox) {
        Map<String, String> context = LogContext.carried();
        outbox.setDispatchRequestId(context.get(LogContext.REQUEST_ID));
        outbox.setDispatchUserId(context.get(LogContext.USER_ID));
        outbox.setDispatchSessionId(context.get(LogContext.SESSION_ID));
        outbox.setDispatchDeviceId(context.get(LogContext.DEVICE_ID));
    }

    public static Scope activate(UserProfileSyncOutbox outbox) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        Map<String, String> origin = new LinkedHashMap<>();
        put(origin, LogContext.REQUEST_ID, outbox.getDispatchRequestId());
        put(origin, LogContext.USER_ID, outbox.getDispatchUserId());
        put(origin, LogContext.SESSION_ID, outbox.getDispatchSessionId());
        put(origin, LogContext.DEVICE_ID, outbox.getDispatchDeviceId());
        origin.putIfAbsent(LogContext.REQUEST_ID, LogContext.newRequestId());
        LogContext.adopt(origin);
        return () -> restore(previous);
    }

    private static void put(Map<String, String> context, String key, String value) {
        if (value != null) {
            context.put(key, value);
        }
    }

    private static void restore(Map<String, String> previous) {
        MDC.clear();
        if (previous != null) {
            MDC.setContextMap(previous);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
