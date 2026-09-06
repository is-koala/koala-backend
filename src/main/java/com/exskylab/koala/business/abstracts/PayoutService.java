package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.Refund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutService {

    Payout releaseToWorker(UUID jobAssignmentId);

    Refund refundEmployer(UUID jobAssignmentId, BigDecimal amount, String reason);

    List<Payout> getPayoutsForWorker(UUID workerId);

    Optional<Refund> getLatestRefundForAssignment(UUID jobAssignmentId);
}
