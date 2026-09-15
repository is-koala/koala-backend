package com.exskylab.koala.entities;

import java.util.Set;

public enum AssignmentStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED;

    public static final Set<AssignmentStatus> HIRED = Set.of(CONFIRMED, IN_PROGRESS, COMPLETED);
}
