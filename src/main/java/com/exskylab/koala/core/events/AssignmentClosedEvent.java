package com.exskylab.koala.core.events;

import java.util.UUID;

public record AssignmentClosedEvent(UUID jobId, UUID workerId, UUID assignmentId) {
}
