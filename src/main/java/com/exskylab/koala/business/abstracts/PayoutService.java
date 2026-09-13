package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.entities.Refund;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PayoutService {

    Payout releaseToWorker(UUID jobAssignmentId);

    Refund refundEmployer(UUID jobAssignmentId, BigDecimal amount, String reason);

    Payout confirmSettlement(UUID payoutId, ConfirmPayoutSettlementDto evidence);

    List<Payout> getPayoutsForWorker(UUID workerId);

    List<UUID> findPayoutsAwaitingSettlementLongerThan(Duration age);

    AmountCount completedVolume(Instant fromInclusive, Instant toExclusive, String currency);

    AmountCount awaitingVolume(String currency);

    PageDto<StaffPayoutDto> listForStaff(List<PayoutStatus> statuses, Pageable pageable);

    record AmountCount(BigDecimal amount, long count) {
    }
}
