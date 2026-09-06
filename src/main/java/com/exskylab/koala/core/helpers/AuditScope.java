package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffPermission;

import java.util.Comparator;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public final class AuditScope {

    public static final String NO_RECORD = "";

    private static final String ADMIN_PREFIX = "/admin";
    private static final Set<String> PAGING_PARAMETERS = Set.of("page", "size", "sort");
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private AuditScope() {
    }

    public static boolean isAuditable(boolean allowed, boolean mutating, PermissionSensitivity sensitivity,
                                      boolean targetsARecord, boolean filtered) {
        if (!allowed || mutating || sensitivity != PermissionSensitivity.NORMAL) {
            return true;
        }
        return targetsARecord || filtered;
    }

    public static boolean isMutating(String method, StaffPermission[] permissions) {
        if (method == null || READ_METHODS.contains(method)) {
            return false;
        }
        return Arrays.stream(permissions).anyMatch(StaffPermission::isMutating);
    }

    public static PermissionSensitivity sensitivityOf(StaffPermission[] permissions) {
        return Arrays.stream(permissions)
                .map(StaffPermission::getSensitivity)
                .max(Comparator.comparingInt(PermissionSensitivity::getRank))
                .orElse(PermissionSensitivity.NORMAL);
    }

    public static boolean hasFilter(Map<String, String[]> parameters) {
        if (parameters == null) {
            return false;
        }
        return parameters.entrySet().stream()
                .filter(entry -> !PAGING_PARAMETERS.contains(entry.getKey()))
                .anyMatch(entry -> hasValue(entry.getValue()));
    }

    public static String resourceTypeOf(String pattern) {
        String path = pattern == null || pattern.isBlank() ? ADMIN_PREFIX : pattern;
        int start = path.startsWith(ADMIN_PREFIX + "/") ? ADMIN_PREFIX.length() + 1 : 0;
        if (start == 0) {
            return "admin";
        }
        int end = path.indexOf('/', start);
        String segment = end < 0 ? path.substring(start) : path.substring(start, end);
        return segment.isBlank() ? "admin" : segment;
    }

    public static String resourceIdOf(String pattern, Map<String, String> uriVariables) {
        if (pattern == null || uriVariables == null || uriVariables.isEmpty()) {
            return NO_RECORD;
        }
        int open = pattern.indexOf('{');
        int close = open < 0 ? -1 : pattern.indexOf('}', open);
        if (close < 0) {
            return NO_RECORD;
        }
        String name = pattern.substring(open + 1, close);
        int constraint = name.indexOf(':');
        String value = uriVariables.get(constraint < 0 ? name : name.substring(0, constraint));
        return value == null ? NO_RECORD : value;
    }

    private static boolean hasValue(String[] values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
