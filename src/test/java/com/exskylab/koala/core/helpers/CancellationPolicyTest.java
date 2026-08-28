package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.CancellationProperties;
import com.exskylab.koala.entities.CancellationParty;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class CancellationPolicyTest {

    private final CancellationPolicy policy = new CancellationPolicy(new CancellationProperties(), passthroughSettings());

    private static final BigDecimal CHARGED = new BigDecimal("1075.00");

    @Test
    void anEmployerCancellingWellAheadPaysNoFee() {
        var outcome = policy.evaluate(CancellationParty.EMPLOYER, CHARGED, Duration.ofHours(48));

        assertEquals(0, outcome.feeAmount().signum());
        assertEquals(0, CHARGED.compareTo(outcome.refundAmount()));
    }

    @Test
    void anEmployerCancellingInsideTheWindowPaysHalf() {
        var outcome = policy.evaluate(CancellationParty.EMPLOYER, CHARGED, Duration.ofHours(3));

        assertEquals(new BigDecimal("537.50"), outcome.feeAmount());
        assertEquals(new BigDecimal("537.50"), outcome.refundAmount());
    }

    @Test
    void exactlyAtTheWindowBoundaryTheCancellationIsStillFree() {
        var outcome = policy.evaluate(CancellationParty.EMPLOYER, CHARGED, Duration.ofHours(24));

        assertEquals(0, outcome.feeAmount().signum());
    }

    @Test
    void anEmployerCancellingAfterTheStartForfeitsEverything() {
        var outcome = policy.evaluate(CancellationParty.EMPLOYER, CHARGED, Duration.ofMinutes(-10));

        assertEquals(0, CHARGED.compareTo(outcome.feeAmount()));
        assertEquals(0, outcome.refundAmount().signum());
    }

    @Test
    void aWorkerCancellingNeverCostsTheEmployerAnything() {
        for (Duration notice : new Duration[]{Duration.ofHours(48), Duration.ofMinutes(30), Duration.ofHours(-5)}) {
            var outcome = policy.evaluate(CancellationParty.WORKER, CHARGED, notice);
            assertEquals(0, outcome.feeAmount().signum(), "notice " + notice);
            assertEquals(0, CHARGED.compareTo(outcome.refundAmount()), "notice " + notice);
        }
    }

    @Test
    void feeAndRefundAlwaysSumToTheCollectedAmount() {
        for (String amount : new String[]{"1075.00", "0.01", "33.33", "99999.99"}) {
            BigDecimal charged = new BigDecimal(amount);
            var outcome = policy.evaluate(CancellationParty.EMPLOYER, charged, Duration.ofHours(2));
            assertEquals(0, charged.compareTo(outcome.feeAmount().add(outcome.refundAmount())),
                    "amount " + amount);
        }
    }

    @Test
    void whenNothingWasCollectedThereIsNothingToRefund() {
        var outcome = policy.evaluate(CancellationParty.EMPLOYER, null, Duration.ofMinutes(5));

        assertEquals(0, outcome.feeAmount().signum());
        assertEquals(0, outcome.refundAmount().signum());
    }

    @Test
    void theNoticeIsMeasuredAgainstTheJobsWallClockZone() {
        CancellationProperties properties = new CancellationProperties();
        properties.setZoneId(ZoneId.of("Europe/Istanbul"));
        CancellationPolicy zoned = new CancellationPolicy(properties, passthroughSettings());

        LocalDateTime start = LocalDateTime.of(2026, 8, 28, 9, 0);
        Instant now = start.atZone(ZoneId.of("Europe/Istanbul")).toInstant().minus(Duration.ofHours(30));

        assertEquals(30, zoned.noticeBefore(start, now).toHours());
    }

    @Test
    void aJobWithoutAStartTimeIsTreatedAsFarAway() {
        assertTrue(policy.noticeBefore(null, Instant.now()).toHours() > 24);
    }

    private static com.exskylab.koala.business.abstracts.SettingsService passthroughSettings() {
        com.exskylab.koala.business.abstracts.SettingsService settings =
                org.mockito.Mockito.mock(com.exskylab.koala.business.abstracts.SettingsService.class);
        org.mockito.Mockito.when(settings.intValue(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> i.getArgument(1));
        org.mockito.Mockito.when(settings.decimalValue(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(1));
        return settings;
    }
}
