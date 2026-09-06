package com.exskylab.koala.entities;

public enum PermissionSensitivity {

    NORMAL(0),
    PERSONAL_DATA(1),
    FINANCIAL(2);

    private final int rank;

    PermissionSensitivity(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
