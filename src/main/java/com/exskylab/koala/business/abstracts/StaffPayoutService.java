package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.Refund;

import java.math.BigDecimal;
import java.util.UUID;

public interface StaffPayoutService {

    Payout releaseToWorker(StaffPrincipal actor, UUID jobAssignmentId);

    Refund refundEmployer(StaffPrincipal actor, UUID jobAssignmentId, BigDecimal amount, String reason);
}
