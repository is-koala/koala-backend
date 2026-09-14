package com.exskylab.koala.core.dtos.address.response;

public enum AddressCandidatePrecision {
    BUILDING,
    STREET,
    NEIGHBORHOOD;

    public boolean isFinerThan(AddressCandidatePrecision other) {
        return ordinal() < other.ordinal();
    }
}
