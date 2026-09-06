package com.exskylab.koala.core.security;

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
