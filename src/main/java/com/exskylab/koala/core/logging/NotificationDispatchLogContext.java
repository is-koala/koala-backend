package com.exskylab.koala.core.logging;

import com.exskylab.koala.entities.Notification;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NotificationDispatchLogContext {

    private NotificationDispatchLogContext() {
    }

    public static void capture(Notification notification) {
        Map<String, String> context = LogContext.carried();
        notification.setDispatchRequestId(context.get(LogContext.REQUEST_ID));
        notification.setDispatchUserId(context.get(LogContext.USER_ID));
        notification.setDispatchSessionId(context.get(LogContext.SESSION_ID));
        notification.setDispatchDeviceId(context.get(LogContext.DEVICE_ID));
    }

    public static Scope activate(Notification notification) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        Map<String, String> origin = new LinkedHashMap<>();
        put(origin, LogContext.REQUEST_ID, notification.getDispatchRequestId());
        put(origin, LogContext.USER_ID, notification.getDispatchUserId());
        put(origin, LogContext.SESSION_ID, notification.getDispatchSessionId());
        put(origin, LogContext.DEVICE_ID, notification.getDispatchDeviceId());
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
