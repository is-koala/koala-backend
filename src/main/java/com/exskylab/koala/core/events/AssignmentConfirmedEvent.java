package com.exskylab.koala.core.events;

import java.util.UUID;

public record AssignmentConfirmedEvent(UUID jobId, UUID workerId) {
}
