package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.core.properties.IyzicoProperties;
import com.exskylab.koala.core.properties.SettlementRetryProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoApprovalResponseDto;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.SettlementTaskDao;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final IyzicoPaymentService iyzicoPaymentService;
    private final IyzicoProperties iyzicoProperties;
    private final SettlementRetryProperties retryProperties;

    public SettlementManager(SettlementTaskDao settlementTaskDao, PayoutDao payoutDao,
                             IyzicoPaymentService iyzicoPaymentService, IyzicoProperties iyzicoProperties,
                             SettlementRetryProperties retryProperties) {
        this.settlementTaskDao = settlementTaskDao;
        this.payoutDao = payoutDao;
        this.iyzicoPaymentService = iyzicoPaymentService;
        this.iyzicoProperties = iyzicoProperties;
        this.retryProperties = retryProperties;
    }

    @Override
    @Transactional
    public Optional<SettlementTask> enqueue(Payment payment, JobAssignment assignment,
                                            SettlementOperation operation) {

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
    @Transactional
    public boolean runTask(UUID taskId) {
        SettlementTask task = settlementTaskDao.findWithDetailsById(taskId).orElse(null);
        if (task == null || task.getStatus() != SettlementTaskStatus.PENDING) {
            return false;
        }

        task.setAttempts(task.getAttempts() + 1);

        try {
            IyzicoApprovalResponseDto response = task.getOperation() == SettlementOperation.APPROVE
                    ? iyzicoPaymentService.approvePaymentSettlement(task.getProviderTransactionId())
                    : iyzicoPaymentService.disapprovePaymentSettlement(task.getProviderTransactionId());

            task.setStatus(SettlementTaskStatus.SUCCEEDED);
            task.setCompletedAt(Instant.now());
            task.setLastError(null);
            task.setProviderReference(response == null ? null : response.getPaymentTransactionId());
            settlementTaskDao.save(task);

            if (task.getOperation() == SettlementOperation.APPROVE) {
                markPayoutProcessing(task);
            }

            log.info("Settlement task {} succeeded on attempt {}", taskId, task.getAttempts());
            return true;

        } catch (Exception e) {
            task.setLastError(truncate(e.getMessage()));

            if (task.getAttempts() >= retryProperties.getMaxAttempts()) {
                task.setStatus(SettlementTaskStatus.FAILED);
                log.error("Settlement task {} gave up after {} attempts: {}",
                        taskId, task.getAttempts(), e.getMessage());
            } else {
                task.setNextAttemptAt(Instant.now().plus(backoffFor(task.getAttempts())));
                log.warn("Settlement task {} failed on attempt {}, next try at {}: {}",
                        taskId, task.getAttempts(), task.getNextAttemptAt(), e.getMessage());
            }

            settlementTaskDao.save(task);
            return false;
        }
    }

    private void markPayoutProcessing(SettlementTask task) {
        if (task.getJobAssignment() == null) {
            return;
        }

        payoutDao.findByJobAssignmentId(task.getJobAssignment().getId()).ifPresent(payout -> {
            payout.setStatus(PayoutStatus.PROCESSING);
            payout.setProviderReference(task.getProviderReference());
            payoutDao.save(payout);
        });
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
