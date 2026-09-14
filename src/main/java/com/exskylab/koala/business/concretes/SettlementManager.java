package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.core.constants.PayoutMessages;
import com.exskylab.koala.core.properties.IyzicoProperties;
import com.exskylab.koala.core.properties.SettlementRetryProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoApprovalResponseDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoRefundRequestDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoRefundResponseDto;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.dataAccess.SettlementTaskDao;
import com.exskylab.koala.entities.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class SettlementManager implements SettlementService {

    private final SettlementTaskDao settlementTaskDao;
    private final PayoutDao payoutDao;
    private final RefundDao refundDao;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final IyzicoProperties iyzicoProperties;
    private final SettlementRetryProperties retryProperties;
    private final RefundManager refundManager;

    public SettlementManager(SettlementTaskDao settlementTaskDao, PayoutDao payoutDao, RefundDao refundDao,
                             IyzicoPaymentService iyzicoPaymentService, IyzicoProperties iyzicoProperties,
                             SettlementRetryProperties retryProperties,
                             RefundManager refundManager) {
        this.settlementTaskDao = settlementTaskDao;
        this.payoutDao = payoutDao;
        this.refundDao = refundDao;
        this.iyzicoPaymentService = iyzicoPaymentService;
        this.iyzicoProperties = iyzicoProperties;
        this.retryProperties = retryProperties;
        this.refundManager = refundManager;
    }

    @Override
    @Transactional
    public Optional<SettlementTask> enqueue(Payment payment, JobAssignment assignment,
                                            SettlementOperation operation) {
        return enqueue(payment, assignment, operation, null);
    }

    @Override
    @Transactional
    public Optional<SettlementTask> enqueueRefund(Payment payment, JobAssignment assignment, BigDecimal amount) {
        return enqueue(payment, assignment, SettlementOperation.REFUND, amount);
    }

    private Optional<SettlementTask> enqueue(Payment payment, JobAssignment assignment,
                                             SettlementOperation operation, BigDecimal amount) {

        if (!iyzicoProperties.isSettlementEnabled()) {
            log.warn("Provider settlement is disabled, {} not queued for payment {}.", operation, payment.getId());
            return Optional.empty();
        }

        String transactionId = payment.getProviderTransactionId();
        if (transactionId == null || transactionId.isBlank()) {
            log.warn("Payment {} carries no provider transaction id, {} not queued.", payment.getId(), operation);
            return Optional.empty();
        }

        Optional<SettlementTask> existing =
                settlementTaskDao.findByPaymentIdAndOperation(payment.getId(), operation);
        if (existing.isPresent()) {
            log.info("Settlement {} already queued for payment {}.", operation, payment.getId());
            return existing;
        }

        SettlementTask task = new SettlementTask();
        task.setPayment(payment);
        task.setJobAssignment(assignment);
        task.setOperation(operation);
        task.setProviderTransactionId(transactionId);
        task.setStatus(SettlementTaskStatus.PENDING);
        task.setAttempts(0);
        task.setAmount(amount);
        task.setNextAttemptAt(Instant.now());

        SettlementTask saved = settlementTaskDao.save(task);
        log.info("Settlement task {} queued: {} for payment {}", saved.getId(), operation, payment.getId());
        return Optional.of(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueTaskIds() {
        return settlementTaskDao.findDueIds(SettlementTaskStatus.PENDING, Instant.now(),
                PageRequest.of(0, retryProperties.getBatchSize()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementTask> getTasksForPayment(UUID paymentId) {
        return settlementTaskDao.findAllByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    @Override
    @Transactional
    public boolean runTask(UUID taskId) {
        SettlementTask task = settlementTaskDao.findWithDetailsById(taskId).orElse(null);
        if (task == null || task.getStatus() != SettlementTaskStatus.PENDING) {
            return false;
        }

        task.setAttempts(task.getAttempts() + 1);

        try {
            if (task.getOperation() == SettlementOperation.REFUND && task.getPayment() != null) {
                refundManager.markProcessing(task.getPayment().getId());
            }
            String reference = dispatch(task);

            task.setStatus(SettlementTaskStatus.SUCCEEDED);
            task.setCompletedAt(Instant.now());
            task.setLastError(null);
            task.setProviderReference(reference);
            settlementTaskDao.save(task);

            if (task.getOperation() == SettlementOperation.APPROVE) {
                markPayoutProcessing(task);
            } else if (task.getOperation() == SettlementOperation.REFUND) {
                markRefundCompleted(task);
            }

            log.info("Settlement task {} succeeded on attempt {}", taskId, task.getAttempts());
            return true;

        } catch (Exception e) {
            task.setLastError(truncate(e.getMessage()));

            if (task.getAttempts() >= retryProperties.getMaxAttempts()) {
                task.setStatus(SettlementTaskStatus.FAILED);
                markSettlementGivenUp(task);
                log.error("Settlement task {} gave up after {} attempts",
                        taskId, task.getAttempts(), e);
            } else {
                task.setNextAttemptAt(Instant.now().plus(backoffFor(task.getAttempts())));
                log.warn("Settlement task {} failed on attempt {}, next try at {} ({})",
                        taskId, task.getAttempts(), task.getNextAttemptAt(), e.getClass().getSimpleName());
            }

            settlementTaskDao.save(task);
            return false;
        }
    }

    private String dispatch(SettlementTask task) {
        String conversationId = task.getPayment() == null ? task.getId().toString()
                : task.getPayment().getConversationId();

        switch (task.getOperation()) {
            case APPROVE -> {
                IyzicoApprovalResponseDto response = iyzicoPaymentService
                        .approvePaymentSettlement(task.getProviderTransactionId(), conversationId);
                return response == null ? null : response.getPaymentTransactionId();
            }
            case DISAPPROVE -> {
                IyzicoApprovalResponseDto response = iyzicoPaymentService
                        .disapprovePaymentSettlement(task.getProviderTransactionId(), conversationId);
                return response == null ? null : response.getPaymentTransactionId();
            }
            default -> {
                IyzicoRefundRequestDto request = new IyzicoRefundRequestDto();
                request.setPaymentTransactionId(task.getProviderTransactionId());
                request.setConversationId(conversationId);
                request.setPrice(task.getAmount());
                request.setCurrency(task.getPayment() == null ? "TRY" : task.getPayment().getCurrency());

                IyzicoRefundResponseDto response = iyzicoPaymentService.refund(request);
                return response == null ? null : response.getPaymentTransactionId();
            }
        }
    }

    private void markPayoutProcessing(SettlementTask task) {
        if (task.getJobAssignment() == null) {
            return;
        }

        payoutDao.findByJobAssignmentId(task.getJobAssignment().getId()).ifPresent(payout -> {
            payout.setStatus(PayoutStatus.PROCESSING);
            payout.setProviderReference(task.getProviderReference());
            payout.setProcessingAt(Instant.now());
            payoutDao.save(payout);
        });
    }

    private void markRefundCompleted(SettlementTask task) {
        latestRefund(task).ifPresent(refund -> {
            refund.setStatus(RefundStatus.COMPLETED);
            refund.setProviderRefundId(task.getProviderReference());
            refund.setCompletedAt(Instant.now());
            refundDao.save(refund);
            log.info("Refund {} completed with provider reference {}",
                    refund.getId(), task.getProviderReference());
        });
    }

    private void markSettlementGivenUp(SettlementTask task) {
        if (task.getOperation() == SettlementOperation.DISAPPROVE) {
            return;
        }

        if (task.getOperation() == SettlementOperation.APPROVE) {
            if (task.getJobAssignment() == null) {
                return;
            }

            payoutDao.findByJobAssignmentId(task.getJobAssignment().getId()).ifPresent(payout -> {
                payout.setStatus(PayoutStatus.FAILED);
                payout.setFailureReason(PayoutMessages.SETTLEMENT_FAILED);
                payoutDao.save(payout);
                log.error("Payout {} marked FAILED, settlement gave up", payout.getId());
            });
            return;
        }

        latestRefund(task).ifPresent(refund -> {
            refund.setStatus(RefundStatus.FAILED);
            refundDao.save(refund);
            log.error("Refund {} marked FAILED, settlement gave up", refund.getId());
        });
    }

    private Optional<Refund> latestRefund(SettlementTask task) {
        if (task.getPayment() == null) {
            return Optional.empty();
        }
        return refundDao.findAllByPaymentIdOrderByCreatedAtDesc(task.getPayment().getId())
                .stream().findFirst();
    }

    Duration backoffFor(int attempts) {
        Duration backoff = retryProperties.getInitialBackoff().multipliedBy(1L << Math.min(attempts - 1, 20));
        return backoff.compareTo(retryProperties.getMaxBackoff()) > 0
                ? retryProperties.getMaxBackoff()
                : backoff;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
