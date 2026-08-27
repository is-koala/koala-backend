package com.exskylab.koala.entities;

public enum PaymentStatus {
    PENDING_COLLECTION,
    HELD_IN_ESCROW,
    RELEASED_TO_WORKER,
    FAILED,
    REFUNDED
}
