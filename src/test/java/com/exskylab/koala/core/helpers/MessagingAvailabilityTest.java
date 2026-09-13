package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.MessagingProperties;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.MessagingAvailabilityReason;
import com.exskylab.koala.entities.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingAvailabilityTest {

    private final MessagingProperties properties = new MessagingProperties();
    private final MessagingAvailability availability = new MessagingAvailability(properties);

    private JobAssignment assignment(AssignmentStatus status, PaymentStatus paymentStatus, LocalDateTime endsAt) {
        Job job = new Job();
        job.setEndDateTime(endsAt);
        JobAssignment assignment = new JobAssignment();
        assignment.setJob(job);
        assignment.setStatus(status);
        assignment.setPaymentStatus(paymentStatus);
        return assignment;
    }

    private LocalDateTime tomorrow() {
        return LocalDateTime.now(properties.zone()).plusDays(1);
    }

    @Test
    void noAssignmentMeansPaymentIsStillRequired() {
        assertEquals(MessagingAvailabilityReason.PAYMENT_REQUIRED, availability.reasonOf(null));
        assertFalse(availability.isAvailable(null));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING_COLLECTION", "FAILED"})
    void anUnpaidAssignmentRequiresPayment(PaymentStatus paymentStatus) {
        JobAssignment assignment = assignment(AssignmentStatus.CONFIRMED, paymentStatus, tomorrow());

        assertEquals(MessagingAvailabilityReason.PAYMENT_REQUIRED, availability.reasonOf(assignment));
    }

    @Test
    void anAssignmentWhosePaymentWasNeverStartedRequiresPayment() {
        JobAssignment assignment = assignment(AssignmentStatus.PENDING_CONFIRMATION, null, tomorrow());

        assertEquals(MessagingAvailabilityReason.PAYMENT_REQUIRED, availability.reasonOf(assignment));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"HELD_IN_ESCROW", "RELEASED_TO_WORKER"})
    void escrowedOrReleasedMoneyOpensMessaging(PaymentStatus paymentStatus) {
        JobAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, paymentStatus, tomorrow());

        assertEquals(MessagingAvailabilityReason.AVAILABLE, availability.reasonOf(assignment));
        assertTrue(availability.isAvailable(assignment));
    }

    @ParameterizedTest
    @EnumSource(value = AssignmentStatus.class, names = {"CANCELLED", "REJECTED"})
    void anEndedAssignmentIsClosedEvenWhenMoneyWasHeld(AssignmentStatus status) {
        JobAssignment assignment = assignment(status, PaymentStatus.HELD_IN_ESCROW, tomorrow());

        assertEquals(MessagingAvailabilityReason.CLOSED, availability.reasonOf(assignment));
    }

    @Test
    void aRefundClosesMessaging() {
        JobAssignment assignment = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.REFUNDED, tomorrow());

        assertEquals(MessagingAvailabilityReason.CLOSED, availability.reasonOf(assignment));
    }

    @Test
    void closureWinsOverPaymentWhenAnUnpaidAssignmentIsCancelled() {
        JobAssignment assignment = assignment(AssignmentStatus.CANCELLED, PaymentStatus.PENDING_COLLECTION, tomorrow());

        assertEquals(MessagingAvailabilityReason.CLOSED, availability.reasonOf(assignment));
    }

    @Test
    void thePaidCrewGoesReadOnlyAfterTheGraceWindow() {
        properties.setGraceDaysAfterJob(7);
        JobAssignment assignment = assignment(AssignmentStatus.COMPLETED, PaymentStatus.RELEASED_TO_WORKER,
                LocalDateTime.now(properties.zone()).minusDays(8));

        assertEquals(MessagingAvailabilityReason.CLOSED, availability.reasonOf(assignment));
    }

    @Test
    void aPaidAssignmentInsideTheGraceWindowStaysOpen() {
        properties.setGraceDaysAfterJob(7);
        JobAssignment assignment = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW,
                LocalDateTime.now(properties.zone()).minusDays(3));

        assertEquals(MessagingAvailabilityReason.AVAILABLE, availability.reasonOf(assignment));
    }

    @Test
    void aJobWithoutAnEndNeverClosesTheWindow() {
        JobAssignment assignment = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, null);

        assertEquals(MessagingAvailabilityReason.AVAILABLE, availability.reasonOf(assignment));
        assertFalse(availability.isWindowClosed(null));
    }
}
