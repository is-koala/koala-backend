package com.exskylab.koala.entities;

public enum StaffExportList {

    JOB_ASSIGNMENTS("job-assignments", StaffPermission.PAYMENT_READ),
    PAYOUTS("payouts", StaffPermission.PAYOUT_RECONCILE),
    REPORTS("reports", StaffPermission.CONTENT_MODERATE),
    DISPUTES("disputes", StaffPermission.DISPUTE_RESOLVE);

    private final String resourceType;
    private final StaffPermission permission;

    StaffExportList(String resourceType, StaffPermission permission) {
        this.resourceType = resourceType;
        this.permission = permission;
    }

    public String getResourceType() {
        return resourceType;
    }

    public StaffPermission getPermission() {
        return permission;
    }
}
