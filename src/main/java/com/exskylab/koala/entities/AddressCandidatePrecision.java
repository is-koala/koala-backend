package com.exskylab.koala.entities;

public enum AddressCandidatePrecision {
    BUILDING,
    STREET,
    NEIGHBORHOOD;

    public boolean isFinerThan(AddressCandidatePrecision other) {
        return ordinal() < other.ordinal();
    }
}
