package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.PermissionSensitivity;
import com.exskylab.koala.entities.StaffPermission;

public final class StaffAuditContext {

    private static final ThreadLocal<StaffAuditContext> CURRENT = new ThreadLocal<>();

    private StaffAuditRecord pending;
    private String before;
    private String after;

    private StaffAuditContext() {
    }

    public static void open() {
        CURRENT.set(new StaffAuditContext());
    }

    public static void close() {
        CURRENT.remove();
    }

    public static StaffAuditContext current() {
        return CURRENT.get();
    }

    public static boolean pend(StaffAuditRecord record) {
        StaffAuditContext context = CURRENT.get();
        if (context == null) {
            return false;
        }
        context.pending = record;
        return true;
    }

    public static void capture(String before, String after) {
        StaffAuditContext context = CURRENT.get();
        if (context == null || context.pending == null) {
            return;
        }
        context.before = before;
        context.after = after;
    }

    public static void about(AuditSubjects subjects) {
        StaffAuditContext context = CURRENT.get();
        if (context == null || context.pending == null || subjects == null) {
            return;
        }

        StaffAuditRecord source = context.pending;
        context.pending = new StaffAuditRecord(
                source.actorStaffId(),
                source.actorDisplayName(),
                source.permission(),
                source.action(),
                source.resourceType(),
                source.resourceId(),
                source.allowed(),
                source.mutating(),
                source.sensitivity(),
                source.ip(),
                source.userAgent(),
                subjects);
    }

    public static void reclassify(StaffPermission permission, String action, boolean allowed) {
        StaffAuditContext context = CURRENT.get();
        if (context == null || context.pending == null) {
            return;
        }

        StaffAuditRecord source = context.pending;
        context.pending = new StaffAuditRecord(
                source.actorStaffId(),
                source.actorDisplayName(),
                permission.getCode(),
                action,
                source.resourceType(),
                source.resourceId(),
                allowed,
                permission.isMutating(),
                higherOf(source.sensitivity(), permission.getSensitivity()),
                source.ip(),
                source.userAgent(),
                source.subjects());
    }

    private static PermissionSensitivity higherOf(PermissionSensitivity current, PermissionSensitivity next) {
        if (current == null || next.getRank() > current.getRank()) {
            return next;
        }
        return current;
    }

    public StaffAuditRecord pending() {
        return pending;
    }

    public String before() {
        return before;
    }

    public String after() {
        return after;
    }
}
