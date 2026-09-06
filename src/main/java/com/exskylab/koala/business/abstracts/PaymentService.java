package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    Payment startAttempt(JobAssignment jobAssignment, CommissionCalculator.Breakdown breakdown);

    void attachProviderToken(Payment payment, String providerToken);

    Payment completeFromCallback(String providerToken);

    List<Payment> getAttemptsForAssignment(UUID jobAssignmentId);

    Payment getSettledPaymentFor(UUID jobAssignmentId);

    boolean hasPendingPaymentForUser(UUID userId);
}
