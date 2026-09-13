package com.exskylab.koala.core.events;

import java.util.UUID;

public record AssignmentPaidEvent(UUID jobId, UUID workerId, UUID assignmentId) {
}
