package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentStateMachineTest {

    private final PaymentStateMachine stateMachine = new PaymentStateMachine();

    private JobAssignment assignment(AssignmentStatus status, PaymentStatus paymentStatus) {
        JobAssignment assignment = new JobAssignment();
        assignment.setStatus(status);
        assignment.setPaymentStatus(paymentStatus);
        return assignment;
    }

    @Test
    void onlyAConfirmedAssignmentAwaitingCollectionCanStartAPayment() {
        assertTrue(stateMachine.canInitiate(
                assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION)));
        assertTrue(stateMachine.canInitiate(
                assignment(AssignmentStatus.CONFIRMED, PaymentStatus.FAILED)));
    }

    @Test
    void anUnconfirmedAssignmentCannotStartAPayment() {
        assertFalse(stateMachine.canInitiate(
                assignment(AssignmentStatus.PENDING_CONFIRMATION, PaymentStatus.PENDING_COLLECTION)));
        assertFalse(stateMachine.canInitiate(
                assignment(AssignmentStatus.REJECTED, PaymentStatus.PENDING_COLLECTION)));
    }

    @Test
    void anAlreadyPaidAssignmentCannotBeChargedAgain() {
        assertFalse(stateMachine.canInitiate(
                assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW)));
        assertFalse(stateMachine.canTransition(PaymentStatus.HELD_IN_ESCROW, PaymentStatus.HELD_IN_ESCROW));
    }

    @Test
    void moneyOnlyMovesForward() {
        assertTrue(stateMachine.canTransition(PaymentStatus.PENDING_COLLECTION, PaymentStatus.HELD_IN_ESCROW));
        assertTrue(stateMachine.canTransition(PaymentStatus.HELD_IN_ESCROW, PaymentStatus.REFUNDED));

        assertFalse(stateMachine.canTransition(PaymentStatus.REFUNDED, PaymentStatus.HELD_IN_ESCROW));
        assertFalse(stateMachine.canTransition(PaymentStatus.HELD_IN_ESCROW, PaymentStatus.PENDING_COLLECTION));
        assertFalse(stateMachine.canTransition(PaymentStatus.REFUNDED, PaymentStatus.PENDING_COLLECTION));
    }

    @Test
    void anIllegalTransitionThrowsInsteadOfSilentlyApplying() {
        JobAssignment paid = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);

        assertThrows(ConflictException.class,
                () -> stateMachine.transition(paid, PaymentStatus.PENDING_COLLECTION));
        assertEquals(PaymentStatus.HELD_IN_ESCROW, paid.getPaymentStatus(),
                "Reddedilen gecis kaydi degistirmemeliydi.");
    }

    @Test
    void aLegalTransitionIsApplied() {
        JobAssignment pending = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);

        stateMachine.transition(pending, PaymentStatus.HELD_IN_ESCROW);

        assertEquals(PaymentStatus.HELD_IN_ESCROW, pending.getPaymentStatus());
    }
}
