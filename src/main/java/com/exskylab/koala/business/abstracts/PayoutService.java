package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.dtos.payout.response.AdminPayoutDetailDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutReleaseOrigin;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.entities.Refund;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutService {

    Payout releaseToWorker(UUID jobAssignmentId, PayoutReleaseOrigin origin);

    Refund refundEmployer(UUID jobAssignmentId, BigDecimal amount, String reason);

    Payout confirmSettlement(UUID payoutId, ConfirmPayoutSettlementDto evidence, UUID reconciledByStaffId);

    List<Payout> getPayoutsForWorker(UUID workerId);

    List<UUID> findPayoutsAwaitingSettlementLongerThan(Duration age);

    AmountCount completedVolume(Instant fromInclusive, Instant toExclusive, String currency);

    AmountCount awaitingVolume(String currency);

    PageDto<StaffPayoutDto> listForStaff(List<PayoutStatus> statuses, String query, Pageable pageable);

    AdminPayoutDetailDto getStaffDetail(UUID payoutId);

    Optional<Payout> findForAssignment(UUID jobAssignmentId);

    List<Payout> findForAssignments(Collection<UUID> jobAssignmentIds);

    List<Refund> getRefundsForPayments(Collection<UUID> paymentIds);

    record AmountCount(BigDecimal amount, long count) {
    }

    long countForWorker(UUID workerId);
}
