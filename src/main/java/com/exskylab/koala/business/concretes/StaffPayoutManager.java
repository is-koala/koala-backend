package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.StaffPayoutService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.StaffPermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
public class StaffPayoutManager implements StaffPayoutService {

    private final PayoutService payoutService;

    public StaffPayoutManager(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @Override
    public Payout releaseToWorker(StaffPrincipal actor, UUID jobAssignmentId) {
        requirePermission(actor, StaffPermission.PAYOUT_RELEASE, jobAssignmentId);
        return payoutService.releaseToWorker(jobAssignmentId);
    }

    @Override
    public Refund refundEmployer(StaffPrincipal actor, UUID jobAssignmentId, BigDecimal amount, String reason) {
        requirePermission(actor, StaffPermission.PAYMENT_REFUND, jobAssignmentId);
        return payoutService.refundEmployer(jobAssignmentId, amount, reason);
    }

    private static void requirePermission(StaffPrincipal actor, StaffPermission permission, UUID jobAssignmentId) {
        if (actor == null) {
            log.warn("Para taşıyan {} işlemi köprü aşamasındaki marketplace ROLE_ADMIN ile yapıldı: {}",
                    permission.getCode(), jobAssignmentId);
            return;
        }

        if (!actor.isRegistered() || !actor.holds(permission)) {
            throw new ForbiddenException(StaffMessages.PERMISSION_DENIED);
        }
    }
}
