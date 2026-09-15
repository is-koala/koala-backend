package com.exskylab.koala.core.dtos.payment.response;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "İşçinin eline geçecek tutar; her görünümde dolu.")
        String workerPayout,
        @Schema(types = {"string", "null"},
                description = "Hizmet bedeli oranı. İlan listesi ve ilan detayında görüntüleyen ilan sahibi ya da "
                        + "şirket yetkilisi değilse null; fiyat teklifi ve işveren görünümünde dolu.")
        String serviceFeeRate,
        @Schema(types = {"string", "null"},
                description = "Hizmet bedeli tutarı. İlan listesi ve ilan detayında görüntüleyen ilan sahibi ya da "
                        + "şirket yetkilisi değilse null; fiyat teklifi ve işveren görünümünde dolu.")
        String serviceFeeAmount,
        @Schema(types = {"string", "null"},
                description = "İşverenden çekilen toplam. İlan listesi ve ilan detayında görüntüleyen ilan sahibi ya da "
                        + "şirket yetkilisi değilse null; fiyat teklifi ve işveren görünümünde dolu.")
        String employerTotal) {

    public static PricingBreakdownDto from(String version, String currency, BigDecimal workerPayout,
                                           BigDecimal rate, BigDecimal fee, BigDecimal total) {
        return new PricingBreakdownDto(version, currency, decimal(workerPayout), decimal(rate),
                decimal(fee), decimal(total));
    }

    public static PricingBreakdownDto workerView(String version, String currency, BigDecimal workerPayout) {
        return new PricingBreakdownDto(version, currency, decimal(workerPayout), null, null, null);
    }

    private static String decimal(BigDecimal value) {
        if (value == null) {
            throw new IllegalStateException("Pricing snapshot is incomplete");
        }
        return value.toPlainString();
    }
}
