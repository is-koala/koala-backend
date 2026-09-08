package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.StaffPermission;

import java.math.BigDecimal;
import java.util.UUID;

public interface StaffPayoutService {

    Payout releaseToWorker(StaffPrincipal actor, UUID jobAssignmentId, String reason);

    Refund refundEmployer(StaffPrincipal actor, UUID jobAssignmentId, BigDecimal amount, String reason);

    Payout confirmSettlement(StaffPrincipal actor, UUID payoutId, ConfirmPayoutSettlementDto evidence);

    void requirePermission(StaffPrincipal actor, StaffPermission permission, UUID jobAssignmentId);
}
