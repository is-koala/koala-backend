package com.exskylab.koala.core.logging;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LogContext {

    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    public static final String DEVICE_ID = "deviceId";
    public static final String METHOD = "method";
    public static final String PATH = "path";
    public static final String STATUS = "status";
    public static final String DURATION_MS = "durationMs";
    public static final String CLIENT_IP = "clientIp";

    public static final String HEADER_PREFIX = "x-koala-log-";

    public static final List<String> KEYS = List.of(REQUEST_ID, USER_ID, SESSION_ID,
            DEVICE_ID, METHOD, PATH, STATUS, DURATION_MS, CLIENT_IP);

    public static final List<String> CARRIED_KEYS =
            List.of(REQUEST_ID, USER_ID, SESSION_ID, DEVICE_ID);

    private LogContext() {
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    public static void put(String key, Object value) {
        if (value == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value.toString());
    }

    public static Map<String, String> carried() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : CARRIED_KEYS) {
            String value = MDC.get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    public static void adopt(Map<String, String> values) {
        clear();
        values.forEach(LogContext::put);
    }

    public static void clear() {
        KEYS.forEach(MDC::remove);
    }
}
