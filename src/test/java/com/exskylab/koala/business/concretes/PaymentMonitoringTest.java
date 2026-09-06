package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.FallbackSettings;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultResponseDto;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.entities.PaymentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentMonitoringTest {

    private static final String TOKEN = "iyzico-token-123";

    private PaymentDao paymentDao;
    private IyzicoPaymentService iyzicoPaymentService;
    private SimpleMeterRegistry registry;
    private PaymentManager paymentManager;

    @BeforeEach
    void setUp() {
        paymentDao = mock(PaymentDao.class);
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        registry = new SimpleMeterRegistry();
        paymentManager = new PaymentManager(paymentDao, iyzicoPaymentService, new PaymentStateMachine(),
                mock(SecurityService.class), mock(JobAssignmentDao.class),
                new AssignmentParticipants(mock(CompanyContactService.class)), new PaymentMetrics(registry));
        when(paymentDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void failingToPersistAnAttemptIncrementsTheBoundedInitiationMetric() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setPricingVersion("2026-09-v1");
        CommissionCalculator calculator = FallbackSettings.commissionCalculator();
        when(paymentDao.save(any())).thenThrow(new IllegalStateException("database unavailable"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> paymentManager.startAttempt(assignment, calculator.calculate(new BigDecimal("1000.00"))));

        assertEquals(1.0, counter("initiation", "persistence_error"));
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

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setJobAssignment(assignment);
        payment.setConversationId(UUID.randomUUID().toString());
        payment.setProviderToken(TOKEN);
        payment.setStatus(PaymentAttemptStatus.INITIATED);
        payment.setCurrency("TRY");
        payment.setWorkerPayout(new BigDecimal("1000.00"));
        payment.setCommissionAmount(new BigDecimal("75.00"));
        payment.setCommissionRate(new BigDecimal("0.075"));
        payment.setTotalCharged(new BigDecimal("1075.00"));
        return payment;
    }
}
