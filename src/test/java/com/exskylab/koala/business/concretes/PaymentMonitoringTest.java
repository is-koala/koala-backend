package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.FallbackSettings;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.properties.PaymentVerificationProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultResponseDto;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.entities.PaymentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class PaymentMonitoringTest {

    private static final String TOKEN = "iyzico-token-123";

    private PaymentDao paymentDao;
    private JobAssignmentDao jobAssignmentDao;
    private IyzicoPaymentService iyzicoPaymentService;
    private SimpleMeterRegistry registry;
    private PaymentManager paymentManager;
    private PaymentVerificationProperties verificationProperties;

    @BeforeEach
    void setUp() {
        paymentDao = mock(PaymentDao.class);
        jobAssignmentDao = mock(JobAssignmentDao.class);
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        registry = new SimpleMeterRegistry();
        verificationProperties = new PaymentVerificationProperties();
        paymentManager = new PaymentManager(paymentDao, iyzicoPaymentService, new PaymentStateMachine(),
                mock(SecurityService.class), jobAssignmentDao,
                new AssignmentParticipants(mock(CompanyContactService.class)), new PaymentMetrics(registry),
                verificationProperties);
        when(paymentDao.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void failingToPersistAnAttemptIncrementsTheBoundedInitiationMetric() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setPricingVersion("2026-09-v1");
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
        when(jobAssignmentDao.findByIdForUpdate(assignment.getId())).thenReturn(Optional.of(assignment));
        CommissionCalculator calculator = FallbackSettings.commissionCalculator();
        when(paymentDao.saveAndFlush(any())).thenThrow(new IllegalStateException("database unavailable"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> paymentManager.startAttempt(assignment, calculator.calculate(new BigDecimal("1000.00"))));

        assertEquals(1.0, counter("initiation", "persistence_error"));
    }

    @Test
    void anActiveAttemptBlocksAnotherReservationBeforeTheProviderIsCalled() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setPricingVersion("2026-09-v1");
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
        when(jobAssignmentDao.findByIdForUpdate(assignment.getId())).thenReturn(Optional.of(assignment));
        when(paymentDao.existsByJobAssignmentIdAndStatusIn(any(), any())).thenReturn(true);

        var breakdown = FallbackSettings.commissionCalculator()
                .calculate(new BigDecimal("1000.00"));

        var thrown = assertThrows(com.exskylab.koala.core.exceptions.ConflictException.class,
                () -> paymentManager.startAttempt(assignment, breakdown));

        assertEquals("PAYMENT_ATTEMPT_IN_PROGRESS", thrown.getErrorCode());
        verify(paymentDao, never()).saveAndFlush(any());
    }

    @Test
    void aConcurrentReservationLosingTheDatabaseRaceBecomesAConflict() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setPricingVersion("2026-09-v1");
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
        when(jobAssignmentDao.findByIdForUpdate(assignment.getId())).thenReturn(Optional.of(assignment));
        when(paymentDao.existsByJobAssignmentIdAndStatusIn(any(), any())).thenReturn(false);
        when(paymentDao.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("duplicate payment attempt",
                        new ConstraintViolationException("duplicate key", new SQLException(),
                                "uk_payments_one_active_attempt")));

        var breakdown = FallbackSettings.commissionCalculator()
                .calculate(new BigDecimal("1000.00"));

        var thrown = assertThrows(com.exskylab.koala.core.exceptions.ConflictException.class,
                () -> paymentManager.startAttempt(assignment, breakdown));

        assertEquals("PAYMENT_ATTEMPT_IN_PROGRESS", thrown.getErrorCode());
        assertEquals(0.0, counter("initiation", "persistence_error"));
    }

    @Test
    void reservationRechecksPaymentEligibilityUnderTheAssignmentLock() {
        JobAssignment staleAssignment = new JobAssignment();
        staleAssignment.setId(UUID.randomUUID());
        staleAssignment.setStatus(AssignmentStatus.CONFIRMED);
        staleAssignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);

        JobAssignment lockedAssignment = new JobAssignment();
        lockedAssignment.setId(staleAssignment.getId());
        lockedAssignment.setStatus(AssignmentStatus.CONFIRMED);
        lockedAssignment.setPaymentStatus(PaymentStatus.HELD_IN_ESCROW);
        when(jobAssignmentDao.findByIdForUpdate(staleAssignment.getId()))
                .thenReturn(Optional.of(lockedAssignment));

        var breakdown = FallbackSettings.commissionCalculator()
                .calculate(new BigDecimal("1000.00"));

        assertThrows(com.exskylab.koala.core.exceptions.ConflictException.class,
                () -> paymentManager.startAttempt(staleAssignment, breakdown));
        verify(paymentDao, never()).saveAndFlush(any());
    }

    @Test
    void aDuplicateProviderTokenIsRejectedAsAnUntrustedProviderResponse() {
        Payment payment = new Payment();
        when(paymentDao.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("duplicate provider token",
                        new ConstraintViolationException("duplicate key", new SQLException(),
                                "uk_payments_provider_token")));

        ExternalServiceException thrown = assertThrows(ExternalServiceException.class,
                () -> paymentManager.attachProviderToken(payment, "duplicate-token"));

        assertEquals("PAYMENT_PROVIDER_RESPONSE_INVALID", thrown.getErrorCode());
    }

    @Test
    void attachingAProviderTokenSchedulesTheInitiatedAttemptAfterTheCheckoutWindow() {
        Payment payment = pendingPayment();
        payment.setProviderToken(null);
        Instant before = Instant.now().plus(Duration.ofMinutes(35));

        paymentManager.attachProviderToken(payment, "new-provider-token");

        assertEquals("new-provider-token", payment.getProviderToken());
        assertNotNull(payment.getNextVerificationAt());
        assertFalse(payment.getNextVerificationAt().isBefore(before));
        assertTrue(payment.getNextVerificationAt().isBefore(Instant.now().plus(Duration.ofMinutes(36))));
    }

    @Test
    void scheduledInitiatedAttemptsAreIncludedInTheDurableReconciliationScan() {
        when(paymentDao.findDueVerificationIds(any(), any(), any())).thenReturn(List.of());

        paymentManager.findDueVerificationAttemptIds();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaymentAttemptStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(paymentDao).findDueVerificationIds(statuses.capture(), any(), any());
        assertTrue(statuses.getValue().contains(PaymentAttemptStatus.INITIATED));
    }

    @Test
    void technicalFailureSeriesExistAtZeroBeforeTheSyntheticProbeRuns() {
        assertEquals(0.0, counter("initiation", "provider_error"));
        assertEquals(0.0, counter("initiation", "persistence_error"));
        assertEquals(0.0, counter("callback", "provider_error"));
        assertEquals(0.0, counter("callback", "amount_mismatch"));
    }

    @Test
    void aProviderCallbackFailureIncrementsTheFailureMetric() {
        Payment payment = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        paymentManager.completeFromCallback(TOKEN);

        assertEquals(1.0, counter("callback", "provider_error"));
    }

    @Test
    void aThreeDSecureVerificationFailureIncrementsTheCriticalCallbackMetric() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto result = new IyzicoPaymentResultResponseDto();
        result.setPaymentStatus("SUCCESS");
        result.setMdStatus(0);
        result.setFraudStatus(1);
        result.setPaymentId("pay-1");
        result.setPaymentTransactionId("txn-1");
        result.setConversationId(payment.getConversationId());
        result.setToken(TOKEN);
        result.setBasketId(payment.getJobAssignment().getId().toString());
        result.setCurrency("TRY");
        result.setPrice(new BigDecimal("1075.00"));
        result.setPaidPrice(new BigDecimal("1075.00"));
        result.setItemCount(1);
        result.setItemId(payment.getJobAssignment().getJob().getId().toString());
        result.setItemPrice(new BigDecimal("1075.00"));
        result.setItemPaidPrice(new BigDecimal("1075.00"));
        result.setItemSubMerchantPrice(new BigDecimal("1000.00"));
        result.setItemSubMerchantKey("submerchant-key");
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(result);

        paymentManager.completeFromCallback(TOKEN);

        assertEquals(1.0, counter("callback", "three_ds_verification_failed"));
    }

    private double counter(String operation, String outcome) {
        return registry.get("koala.payment.operations")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private Payment pendingPayment() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
        Job job = new Job();
        job.setId(UUID.randomUUID());
        assignment.setJob(job);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setJobAssignment(assignment);
        payment.setConversationId(UUID.randomUUID().toString());
        payment.setProviderToken(TOKEN);
        payment.setProviderSubmerchantKey("submerchant-key");
        payment.setStatus(PaymentAttemptStatus.INITIATED);
        payment.setCurrency("TRY");
        payment.setWorkerPayout(new BigDecimal("1000.00"));
        payment.setCommissionAmount(new BigDecimal("75.00"));
        payment.setCommissionRate(new BigDecimal("0.075"));
        payment.setTotalCharged(new BigDecimal("1075.00"));
        when(paymentDao.findJobAssignmentIdByProviderToken(TOKEN))
                .thenReturn(Optional.of(assignment.getId()));
        when(paymentDao.findJobAssignmentIdById(payment.getId()))
                .thenReturn(Optional.of(assignment.getId()));
        when(jobAssignmentDao.findByIdForUpdate(assignment.getId())).thenReturn(Optional.of(assignment));
        return payment;
    }
}
