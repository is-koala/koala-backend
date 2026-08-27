package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.CancellationProperties;
import com.exskylab.koala.entities.CancellationParty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Component
public class CancellationPolicy {

    private static final int MONEY_SCALE = 2;

    private final CancellationProperties properties;

    public CancellationPolicy(CancellationProperties properties) {
        this.properties = properties;
    }

    public Duration noticeBefore(LocalDateTime jobStart, Instant now) {
        if (jobStart == null) {
            return Duration.ofDays(365);
        }
        return Duration.between(now, jobStart.atZone(properties.getZoneId()).toInstant());
    }

    public Outcome evaluate(CancellationParty party, BigDecimal collectedAmount, Duration notice) {
        BigDecimal collected = collectedAmount == null
                ? BigDecimal.ZERO
                : collectedAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (collected.signum() <= 0) {
            return new Outcome(BigDecimal.ZERO.setScale(4), zero(), zero(), notice);
        }

        BigDecimal rate = feeRate(party, notice);
        BigDecimal fee = collected.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new Outcome(rate, fee, collected.subtract(fee), notice);
    }

    private BigDecimal feeRate(CancellationParty party, Duration notice) {
        if (party != CancellationParty.EMPLOYER) {
            return BigDecimal.ZERO.setScale(4);
        }
        if (!notice.isPositive()) {
            return BigDecimal.ONE.setScale(4);
        }
        if (notice.toHours() >= properties.getFreeWindowHours()) {
            return BigDecimal.ZERO.setScale(4);
        }
        return properties.getLateFeeRate().setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    public record Outcome(BigDecimal feeRate, BigDecimal feeAmount,
                          BigDecimal refundAmount, Duration notice) {
    }
}
