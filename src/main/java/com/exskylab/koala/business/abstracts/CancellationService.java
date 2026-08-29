package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.cancellation.response.CancellationPreviewDto;
import com.exskylab.koala.entities.Cancellation;

import java.util.Optional;
import java.util.UUID;

public interface CancellationService {

    Cancellation cancelAssignment(UUID jobAssignmentId, String reason);

    boolean expireUnpaidAssignment(UUID jobAssignmentId);

    CancellationPreviewDto previewCancellation(UUID jobAssignmentId);

    Optional<Cancellation> getForAssignment(UUID jobAssignmentId);
}
