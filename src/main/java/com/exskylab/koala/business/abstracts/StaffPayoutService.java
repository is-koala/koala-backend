package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.dtos.payout.response.AdminPayoutDetailDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutReleaseOrigin;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.StaffPermission;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface StaffPayoutService {

    Payout releaseToWorker(StaffPrincipal actor, UUID jobAssignmentId, String reason, PayoutReleaseOrigin origin);

    Refund refundEmployer(StaffPrincipal actor, UUID jobAssignmentId, BigDecimal amount, String reason);

    Payout confirmSettlement(StaffPrincipal actor, UUID payoutId, ConfirmPayoutSettlementDto evidence);

    PageDto<StaffPayoutDto> listForStaff(StaffPrincipal actor, List<PayoutStatus> statuses, String query,
                                         Pageable pageable);

    AdminPayoutDetailDto getForStaff(StaffPrincipal actor, UUID payoutId);

    void requirePermission(StaffPrincipal actor, StaffPermission permission, UUID jobAssignmentId);
}
