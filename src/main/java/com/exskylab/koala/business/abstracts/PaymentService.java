package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    Payment startAttempt(JobAssignment jobAssignment, CommissionCalculator.Breakdown breakdown);

    void attachProviderSubmerchantKey(Payment payment, String providerSubmerchantKey);

    void attachProviderToken(Payment payment, String providerToken);

    void markInitiationUncertain(UUID paymentId);

    void failInitiation(UUID paymentId);

    Payment completeFromCallback(String providerToken);

    List<UUID> findDueVerificationAttemptIds();

    boolean reconcileAttempt(UUID paymentId);

    List<UUID> findStuckInitiationIds();

    boolean abandonStuckInitiation(UUID paymentId);

    List<Payment> getAttemptsForAssignment(UUID jobAssignmentId);

    Payment getSettledPaymentFor(UUID jobAssignmentId);

    boolean hasPendingAttemptForAssignment(UUID jobAssignmentId);

    boolean hasPendingPaymentForUser(UUID userId);

    SucceededVolume succeededVolume(Instant fromInclusive, Instant toExclusive, String currency);

    record SucceededVolume(BigDecimal employerVolume, BigDecimal koalaFee,
                           BigDecimal workerPayout, long count) {
    }
}
