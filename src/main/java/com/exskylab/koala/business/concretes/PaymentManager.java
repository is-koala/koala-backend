package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultRequestDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultResponseDto;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.core.constants.PayoutMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PaymentManager implements PaymentService {

    private static final String CURRENCY = "TRY";

    private final PaymentDao paymentDao;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final PaymentStateMachine stateMachine;

    public PaymentManager(PaymentDao paymentDao, IyzicoPaymentService iyzicoPaymentService,
                          PaymentStateMachine stateMachine) {
        this.paymentDao = paymentDao;
        this.iyzicoPaymentService = iyzicoPaymentService;
        this.stateMachine = stateMachine;
    }

    @Override
    @Transactional
    public Payment startAttempt(JobAssignment jobAssignment, CommissionCalculator.Breakdown breakdown) {
        Payment payment = new Payment();
        payment.setJobAssignment(jobAssignment);
        payment.setConversationId(UUID.randomUUID().toString());
        payment.setStatus(PaymentAttemptStatus.INITIATED);
        payment.setCurrency(CURRENCY);
        payment.setWorkerPayout(breakdown.workerPayout());
        payment.setCommissionAmount(breakdown.commission());
        payment.setCommissionRate(breakdown.rate());
        payment.setTotalCharged(breakdown.totalCharged());

        Payment saved = paymentDao.save(payment);
        log.info("Payment attempt {} started for assignment {}", saved.getId(), jobAssignment.getId());
        return saved;
    }

    @Override
    @Transactional
    public void attachProviderToken(Payment payment, String providerToken) {
        payment.setProviderToken(providerToken);
        paymentDao.save(payment);
    }

    @Override
    @Transactional
    public Payment completeFromCallback(String providerToken) {
        Payment payment = paymentDao.findByProviderToken(providerToken)
                .orElseThrow(() -> new ResourceNotFoundException(PaymentMessages.NOT_FOUND));

        if (payment.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
            log.info("Payment {} already settled, callback ignored.", payment.getId());
            return payment;
        }

        IyzicoPaymentResultRequestDto request = new IyzicoPaymentResultRequestDto();
        request.setToken(providerToken);
        request.setConversationId(payment.getConversationId());

        IyzicoPaymentResultResponseDto result;
        try {
            result = iyzicoPaymentService.retrieveCheckoutFormResult(request);
        } catch (RuntimeException e) {
            log.warn("Payment {} could not be verified with the provider: {}", payment.getId(), e.getMessage());
            return fail(payment, e.getMessage());
        }

        if (!"SUCCESS".equalsIgnoreCase(result.getPaymentStatus())) {
            log.info("Payment {} was not completed by the provider, paymentStatus={}",
                    payment.getId(), result.getPaymentStatus());
            return fail(payment, "paymentStatus=" + result.getPaymentStatus());
        }

        if (result.getFraudStatus() != null && result.getFraudStatus() == -1) {
            log.warn("Payment {} was rejected by fraud filters.", payment.getId());
            return fail(payment, PaymentMessages.FRAUD_REVIEW);
        }

        if (result.getFraudStatus() != null && result.getFraudStatus() == 0) {
            log.warn("Payment {} is under fraud review, escrow is not opened yet.", payment.getId());
            return hold(payment, result);
        }

        String amountMismatch = amountMismatch(payment, result);
        if (amountMismatch != null) {
            log.error("Payment {} rejected: {}", payment.getId(), amountMismatch);
            return fail(payment, amountMismatch);
        }

        return settle(payment, result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getAttemptsForAssignment(UUID jobAssignmentId) {
        return paymentDao.findAllByJobAssignmentIdOrderByCreatedAtDesc(jobAssignmentId);
    }

    private String amountMismatch(Payment payment, IyzicoPaymentResultResponseDto result) {
        BigDecimal charged = result.getPaidPrice();
        if (charged == null) {
            return "provider did not report a paid price";
        }

        if (charged.compareTo(payment.getTotalCharged()) != 0) {
            return "expected " + payment.getTotalCharged() + " but the provider collected " + charged;
        }

        if (result.getCurrency() != null && !CURRENCY.equalsIgnoreCase(result.getCurrency())) {
            return "expected currency " + CURRENCY + " but the provider used " + result.getCurrency();
        }

        return null;
    }

    private Payment hold(Payment payment, IyzicoPaymentResultResponseDto result) {
        payment.setStatus(PaymentAttemptStatus.IN_REVIEW);
        payment.setProviderPaymentId(result.getPaymentId());
        payment.setProviderTransactionId(result.getPaymentTransactionId());
        payment.setFailureReason(PaymentMessages.FRAUD_REVIEW);
        return paymentDao.save(payment);
    }

    private Payment settle(Payment payment, IyzicoPaymentResultResponseDto result) {
        JobAssignment assignment = payment.getJobAssignment();

        payment.setStatus(PaymentAttemptStatus.SUCCEEDED);
        payment.setProviderPaymentId(result.getPaymentId());
        payment.setProviderTransactionId(result.getPaymentTransactionId());
        payment.setCompletedAt(Instant.now());

        stateMachine.transition(assignment, PaymentStatus.HELD_IN_ESCROW);
        assignment.setWorkerPayout(payment.getWorkerPayout());
        assignment.setCommissionAmount(payment.getCommissionAmount());
        assignment.setCommissionRate(payment.getCommissionRate());
        assignment.setTotalCharged(payment.getTotalCharged());
        assignment.setFinalPayout(payment.getWorkerPayout());

        log.info("Payment {} settled for assignment {}", payment.getId(), assignment.getId());
        return paymentDao.save(payment);
    }

    private Payment fail(Payment payment, String reason) {
        payment.setStatus(PaymentAttemptStatus.FAILED);
        payment.setFailureReason(reason == null ? null
                : reason.substring(0, Math.min(reason.length(), 512)));
        payment.setCompletedAt(Instant.now());

        JobAssignment assignment = payment.getJobAssignment();
        if (stateMachine.canTransition(assignment.getPaymentStatus(), PaymentStatus.FAILED)) {
            assignment.setPaymentStatus(PaymentStatus.FAILED);
        }

        return paymentDao.save(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getSettledPaymentFor(UUID jobAssignmentId) {
        return paymentDao.findAllByJobAssignmentIdOrderByCreatedAtDesc(jobAssignmentId).stream()
                .filter(payment -> payment.getStatus() == PaymentAttemptStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.NO_SETTLED_PAYMENT));
    }
}
