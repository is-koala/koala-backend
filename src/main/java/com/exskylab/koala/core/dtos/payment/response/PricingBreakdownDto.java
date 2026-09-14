package com.exskylab.koala.core.dtos.payment.response;

import java.math.BigDecimal;

/**
 * Immutable money quote returned to clients.
 *
 * Decimal values deliberately travel as strings. The mobile client may format
 * them for display, but never has to reproduce backend rounding with a binary
 * floating-point calculation.
 */
public record PricingBreakdownDto(
        String pricingVersion,
        String currency,
        String workerPayout,
        String serviceFeeRate,
        String serviceFeeAmount,
        String employerTotal) {

    public static PricingBreakdownDto from(String version, String currency, BigDecimal workerPayout,
                                           BigDecimal rate, BigDecimal fee, BigDecimal total) {
        return new PricingBreakdownDto(version, currency, decimal(workerPayout), decimal(rate),
                decimal(fee), decimal(total));
    }

    private static String decimal(BigDecimal value) {
        if (value == null) {
            throw new IllegalStateException("Pricing snapshot is incomplete");
        }
        return value.toPlainString();
    }
}
