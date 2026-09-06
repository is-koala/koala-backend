package com.exskylab.koala.entities;

public enum CompanyApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public boolean isApproved() {
        return this == APPROVED;
    }
}
