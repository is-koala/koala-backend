package com.exskylab.koala.core.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PaymentMetrics {

    private static final String METRIC_NAME = "koala.payment.operations";
    private static final String OVERDUE_GAUGE_NAME = "koala.payout.settlement.overdue";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger payoutsAwaitingSettlement = new AtomicInteger();

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder(OVERDUE_GAUGE_NAME, payoutsAwaitingSettlement, AtomicInteger::doubleValue)
                .description("Payouts still waiting for bank settlement evidence past the reconciliation deadline")
                .register(meterRegistry);
        register("initiation", "succeeded");
        register("initiation", "provider_error");
        register("initiation", "persistence_error");
        register("initiation", "abandoned");
        register("callback", "succeeded");
        register("callback", "already_succeeded");
        register("callback", "unknown_token");
        register("callback", "provider_error");
        register("callback", "declined");
        register("callback", "three_ds_verification_failed");
        register("callback", "fraud_rejected");
        register("callback", "in_review");
        register("callback", "amount_mismatch");
        register("payout_settlement", "settled");
        register("payout_settlement", "already_settled");
        register("payout_settlement", "evidence_mismatch");
    }

    public void payoutSettled() {
        record("payout_settlement", "settled");
    }

    public void payoutAlreadySettled() {
        record("payout_settlement", "already_settled");
    }

    public void payoutSettlementEvidenceMismatch() {
        record("payout_settlement", "evidence_mismatch");
    }

    public void payoutsAwaitingSettlement(int overdue) {
        payoutsAwaitingSettlement.set(overdue);
    }

    public void initiationSucceeded() {
        record("initiation", "succeeded");
    }

    public void initiationProviderError() {
        record("initiation", "provider_error");
    }

    public void initiationPersistenceError() {
        record("initiation", "persistence_error");
    }

    public void initiationAbandoned() {
        record("initiation", "abandoned");
    }

    public void callbackSucceeded() {
        record("callback", "succeeded");
    }

    public void callbackAlreadySucceeded() {
        record("callback", "already_succeeded");
    }

    public void callbackUnknownToken() {
        record("callback", "unknown_token");
    }

    public void callbackProviderError() {
        record("callback", "provider_error");
    }

    public void callbackDeclined() {
        record("callback", "declined");
    }

    public void callbackThreeDSecureVerificationFailed() {
        record("callback", "three_ds_verification_failed");
    }

    public void callbackFraudRejected() {
        record("callback", "fraud_rejected");
    }

    public void callbackInReview() {
        record("callback", "in_review");
    }

    public void callbackAmountMismatch() {
        record("callback", "amount_mismatch");
    }

    private void record(String operation, String outcome) {
        meterRegistry.counter(METRIC_NAME, "operation", operation, "outcome", outcome).increment();
    }

    private void register(String operation, String outcome) {
        meterRegistry.counter(METRIC_NAME, "operation", operation, "outcome", outcome);
    }
}
