package com.exskylab.koala.core.dtos.finance.response;

import java.time.Instant;

public record FinanceSummaryDto(Instant from, Instant to, String currency,
                                String employerVolume, String koalaFee, String workerPayout,
                                long succeededPaymentCount,
                                String completedPayoutAmount, long completedPayoutCount,
                                String awaitingPayoutAmount, long awaitingPayoutCount) {
}
