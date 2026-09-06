package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.StaffPayoutService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.properties.StaffBridgeProperties;
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
    private final StaffBridgeProperties staffBridgeProperties;

    public StaffPayoutManager(PayoutService payoutService, StaffBridgeProperties staffBridgeProperties) {
        this.payoutService = payoutService;
        this.staffBridgeProperties = staffBridgeProperties;
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
        if (actor == null) {
            if (!staffBridgeProperties.isOpen()) {
                throw new ForbiddenException(StaffMessages.PERMISSION_DENIED);
            }

            log.warn("Aktörsüz para hareketi köprü aşamasındaki marketplace ROLE_ADMIN ile yapıldı: "
                            + "izin={} atama={} tutar={} gerekçe={}",
                    permission.getCode(), jobAssignmentId,
                    amount == null ? "emanetin tamamı" : amount,
                    reason == null || reason.isBlank() ? "belirtilmedi" : reason);
            return;
        }

        if (!actor.isRegistered() || !actor.holds(permission)) {
            throw new ForbiddenException(StaffMessages.PERMISSION_DENIED);
        }
    }
}
