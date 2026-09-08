package com.exskylab.koala.entities;

import java.util.List;

public enum PayoutStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public static final List<PayoutStatus> AWAITING_SETTLEMENT = List.of(PROCESSING);
}
