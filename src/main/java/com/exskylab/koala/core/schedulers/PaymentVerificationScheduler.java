package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.properties.PaymentVerificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class PaymentVerificationScheduler {

    private final PaymentService paymentService;
    private final PaymentVerificationProperties properties;

    public PaymentVerificationScheduler(PaymentService paymentService,
                                        PaymentVerificationProperties properties) {
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @Scheduled(cron = "${iyzico.payment-verification.cron:30 */5 * * * *}")
    public void sweepAbandonedInitiations() {
        if (!properties.isEnabled()) {
            return;
        }

        for (UUID paymentId : paymentService.findStuckInitiationIds()) {
            try {
                paymentService.abandonStuckInitiation(paymentId);
            } catch (Exception exception) {
                log.error("Payment {} stuck initiation could not be closed", paymentId, exception);
            }
        }
    }

    @Scheduled(cron = "${iyzico.payment-verification.cron:30 */5 * * * *}")
    public void reconcileDueAttempts() {
        if (!properties.isEnabled()) {
            return;
        }

        for (UUID paymentId : paymentService.findDueVerificationAttemptIds()) {
            try {
                paymentService.reconcileAttempt(paymentId);
            } catch (Exception exception) {
                log.error("Payment {} reconciliation could not be run", paymentId, exception);
            }
        }
    }
}
