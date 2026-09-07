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
    public void requirePermission(StaffPrincipal actor, StaffPermission permission, UUID jobAssignmentId) {
        requirePermission(actor, permission, jobAssignmentId, null, null);
    }

    private void requirePermission(StaffPrincipal actor, StaffPermission permission, UUID jobAssignmentId,
                                   BigDecimal amount, String reason) {
        if (actor == null || !actor.isRegistered() || !actor.holds(permission)) {
            log.warn("Personel izni olmadan para hareketi reddedildi: izin={} atama={} tutar={} gerekçe={}",
                    permission.getCode(), jobAssignmentId,
                    amount == null ? "emanetin tamamı" : amount,
                    reason == null || reason.isBlank() ? "belirtilmedi" : reason);
            throw new ForbiddenException(StaffMessages.PERMISSION_DENIED);
        }
    }
}
