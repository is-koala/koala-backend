package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PaymentStatus.PENDING_COLLECTION, Set.of(PaymentStatus.HELD_IN_ESCROW, PaymentStatus.FAILED),
            PaymentStatus.FAILED, Set.of(PaymentStatus.HELD_IN_ESCROW, PaymentStatus.FAILED),
            PaymentStatus.HELD_IN_ESCROW, Set.of(PaymentStatus.REFUNDED),
            PaymentStatus.REFUNDED, Set.of());

    public boolean canInitiate(JobAssignment assignment) {
        return assignment.getStatus() == AssignmentStatus.CONFIRMED
                && (assignment.getPaymentStatus() == PaymentStatus.PENDING_COLLECTION
                    || assignment.getPaymentStatus() == PaymentStatus.FAILED);
    }

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null) {
            return to == PaymentStatus.PENDING_COLLECTION;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void transition(JobAssignment assignment, PaymentStatus to) {
        PaymentStatus from = assignment.getPaymentStatus();
        if (!canTransition(from, to)) {
            throw new IllegalStateException("{payment.invalid.status.transition}");
        }
        assignment.setPaymentStatus(to);
    }
}
