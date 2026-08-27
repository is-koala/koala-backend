package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Cancellation;

import java.util.Optional;
import java.util.UUID;

public interface CancellationService {

    Cancellation cancelAssignment(UUID jobAssignmentId, String reason);

    Optional<Cancellation> getForAssignment(UUID jobAssignmentId);
}
