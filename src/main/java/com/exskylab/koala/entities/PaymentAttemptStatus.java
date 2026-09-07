package com.exskylab.koala.entities;

import java.util.List;

public enum PaymentAttemptStatus {
    INITIATED,
    VERIFY_PENDING,
    IN_REVIEW,
    SUCCEEDED,
    FAILED;

    public static final List<PaymentAttemptStatus> ACTIVE = List.of(INITIATED, VERIFY_PENDING, IN_REVIEW);
}
