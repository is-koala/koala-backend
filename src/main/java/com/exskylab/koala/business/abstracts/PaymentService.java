package com.exskylab.koala.business.abstracts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import com.exskylab.koala.dataAccess.PartyLink;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentService {

    Payment startAttempt(JobAssignment jobAssignment, CommissionCalculator.Breakdown breakdown);

    void attachProviderSubmerchantKey(Payment payment, String providerSubmerchantKey);

    void attachProviderToken(Payment payment, String providerToken);

    void markInitiationUncertain(UUID paymentId);

    void failInitiation(UUID paymentId);

    Payment completeFromCallback(String providerToken);

    Optional<RecordedOutcome> findRecordedOutcome(String providerToken);

    List<UUID> findDueVerificationAttemptIds();

    boolean reconcileAttempt(UUID paymentId);

    List<UUID> findStuckInitiationIds();

    boolean abandonStuckInitiation(UUID paymentId);

    List<Payment> getAttemptsForAssignment(UUID jobAssignmentId);

    List<Payment> getAttemptsForStaff(UUID jobAssignmentId);

    Payment getForStaff(UUID paymentId);

    Page<Payment> searchForStaff(List<PaymentAttemptStatus> statuses, Pageable pageable);

    long countStuckVerifications();

    Payment getSettledPaymentFor(UUID jobAssignmentId);

    Map<UUID, Instant> securedAtForAssignments(Collection<UUID> jobAssignmentIds);

    boolean hasPendingAttemptForAssignment(UUID jobAssignmentId);

    SucceededVolume succeededVolume(Instant fromInclusive, Instant toExclusive, String currency);

    record SucceededVolume(BigDecimal employerVolume, BigDecimal koalaFee,
                           BigDecimal workerPayout, long count) {
    }

    record RecordedOutcome(PaymentAttemptStatus status, UUID jobAssignmentId) {
    }

    long countForEmployer(UUID employerId);

    List<PartyLink> findPendingLinksForUsers(Collection<UUID> userIds);
}
