package com.exskylab.koala.entities;

public enum DisputeResolution {

    RELEASE_TO_WORKER(StaffPermission.PAYOUT_RELEASE, "dispute.resolve.release"),
    REFUND_EMPLOYER(StaffPermission.PAYMENT_REFUND, "dispute.resolve.refund"),
    DISMISSED(null, null);

    private final StaffPermission moneyPermission;
    private final String moneyAuditAction;

    DisputeResolution(StaffPermission moneyPermission, String moneyAuditAction) {
        this.moneyPermission = moneyPermission;
        this.moneyAuditAction = moneyAuditAction;
    }

    public StaffPermission getMoneyPermission() {
        return moneyPermission;
    }

    public boolean movesMoney() {
        return moneyPermission != null;
    }

    public String getMoneyAuditAction() {
        return moneyAuditAction;
    }
}
