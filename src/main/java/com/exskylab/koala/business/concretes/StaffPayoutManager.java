package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.StaffPayoutService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.dtos.payout.response.AdminPayoutDetailDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.StaffPermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class StaffPayoutManager implements StaffPayoutService {

    private final PayoutService payoutService;

    public StaffPayoutManager(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @Override
    public Payout releaseToWorker(StaffPrincipal actor, UUID jobAssignmentId, String reason) {
        requirePermission(actor, StaffPermission.PAYOUT_RELEASE, jobAssignmentId, null, reason);
        return payoutService.releaseToWorker(jobAssignmentId);
    }

    @Override
    public Refund refundEmployer(StaffPrincipal actor, UUID jobAssignmentId, BigDecimal amount, String reason) {
        requirePermission(actor, StaffPermission.PAYMENT_REFUND, jobAssignmentId, amount, reason);
        return payoutService.refundEmployer(jobAssignmentId, amount, reason);
    }

    @Override
    public Payout confirmSettlement(StaffPrincipal actor, UUID payoutId, ConfirmPayoutSettlementDto evidence) {
        requirePermission(actor, StaffPermission.PAYOUT_RECONCILE, payoutId, evidence.getAmount(),
                evidence.getSettlementReference());
        return payoutService.confirmSettlement(payoutId, evidence);
    }

    @Override
    public PageDto<StaffPayoutDto> listForStaff(StaffPrincipal actor, List<PayoutStatus> statuses, String query,
                                                Pageable pageable) {
        if (lacks(actor, StaffPermission.PAYOUT_RECONCILE)) {
            log.warn("Payout queue read refused without staff permission: permission={} statuses={}",
                    StaffPermission.PAYOUT_RECONCILE.getCode(), statuses);
            throw new ForbiddenException(StaffMessages.PERMISSION_DENIED);
        }
        return payoutService.listForStaff(statuses, query, pageable);
    }

    @Override
    public AdminPayoutDetailDto getForStaff(StaffPrincipal actor, UUID payoutId) {
        if (lacks(actor, StaffPermission.PAYOUT_RECONCILE)) {
            log.warn("Payout detail read refused without staff permission: permission={} payout={}",
                    StaffPermission.PAYOUT_RECONCILE.getCode(), payoutId);
            throw new ForbiddenException(StaffMessages.PERMISSION_DENIED);
        }
        return payoutService.getStaffDetail(payoutId);
    }

    @Override
    public void requirePermission(StaffPrincipal actor, StaffPermission permission, UUID jobAssignmentId) {
        requirePermission(actor, permission, jobAssignmentId, null, null);
    }

    private void requirePermission(StaffPrincipal actor, StaffPermission permission, UUID jobAssignmentId,
                                   BigDecimal amount, String reason) {
        if (lacks(actor, permission)) {
            log.warn("Money movement refused without staff permission: permission={} assignment={} amount={} reason={}",
                    permission.getCode(), jobAssignmentId,
                    amount == null ? "full escrow" : amount,
                    reason == null || reason.isBlank() ? "not specified" : reason);
            throw new ForbiddenException(StaffMessages.PERMISSION_DENIED);
        }
    }

    private static boolean lacks(StaffPrincipal actor, StaffPermission permission) {
        return actor == null || !actor.isRegistered() || !actor.holds(permission);
    }
}
