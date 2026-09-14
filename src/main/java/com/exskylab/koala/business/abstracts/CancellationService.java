package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.cancellation.response.CancellationPreviewDto;
import com.exskylab.koala.entities.Cancellation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CancellationService {

    Cancellation cancelAssignment(UUID jobAssignmentId, String reason);

    boolean expireUnpaidAssignment(UUID jobAssignmentId);

    CancellationPreviewDto previewCancellation(UUID jobAssignmentId);

    Optional<Cancellation> getForAssignment(UUID jobAssignmentId);

    Optional<Cancellation> findForStaff(UUID jobAssignmentId);

    List<Cancellation> findAllByRefundIds(Collection<UUID> refundIds);
}
