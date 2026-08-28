package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.core.exceptions.ConflictException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PaymentStatus.PENDING_COLLECTION, Set.of(PaymentStatus.HELD_IN_ESCROW, PaymentStatus.FAILED),
            PaymentStatus.FAILED, Set.of(PaymentStatus.HELD_IN_ESCROW, PaymentStatus.FAILED),
            PaymentStatus.HELD_IN_ESCROW, Set.of(PaymentStatus.RELEASED_TO_WORKER, PaymentStatus.REFUNDED),
            PaymentStatus.RELEASED_TO_WORKER, Set.of(),
            PaymentStatus.REFUNDED, Set.of());

    public boolean canInitiate(JobAssignment assignment) {
        return assignment.getStatus() == AssignmentStatus.CONFIRMED
                && (assignment.getPaymentStatus() == PaymentStatus.PENDING_COLLECTION
                    || assignment.getPaymentStatus() == PaymentStatus.FAILED);
    }

    public boolean canRelease(JobAssignment assignment) {
        return assignment.getStatus() == AssignmentStatus.COMPLETED
                && assignment.getPaymentStatus() == PaymentStatus.HELD_IN_ESCROW;
    }

    public boolean canRefund(JobAssignment assignment) {
        return assignment.getPaymentStatus() == PaymentStatus.HELD_IN_ESCROW;
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
            throw new ConflictException("{payment.invalid.status.transition}");
        }
        assignment.setPaymentStatus(to);
    }
}
