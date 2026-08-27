package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeResolution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DisputeService {

    Dispute openDispute(UUID jobAssignmentId, String reason);

    Dispute resolveDispute(UUID disputeId, DisputeResolution resolution, String note);

    List<Dispute> getForAssignment(UUID jobAssignmentId);

    Page<Dispute> getPending(Pageable pageable);
}
