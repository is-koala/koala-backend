package com.exskylab.koala.entities;

public enum DisputeResolution {

    RELEASE_TO_WORKER(StaffPermission.PAYOUT_RELEASE),
    REFUND_EMPLOYER(StaffPermission.PAYMENT_REFUND),
    DISMISSED(null);

    private final StaffPermission moneyPermission;

    DisputeResolution(StaffPermission moneyPermission) {
        this.moneyPermission = moneyPermission;
    }

    public StaffPermission getMoneyPermission() {
        return moneyPermission;
    }

    public boolean movesMoney() {
        return moneyPermission != null;
    }
}
