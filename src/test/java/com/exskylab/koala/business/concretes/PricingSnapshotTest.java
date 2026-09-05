package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.properties.CommissionProperties;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingSnapshotTest {

    @Test
    void publishedPriceIsCopiedThroughAssignmentWithoutFollowingLaterRateChanges() {
        CommissionProperties properties = new CommissionProperties();
        properties.setRate(new BigDecimal("0.075"));
        PricingManager pricing = new PricingManager(new CommissionCalculator(properties));

        Job job = new Job();
        job.setSalary(new BigDecimal("1000"));
        pricing.snapshot(job);

        properties.setRate(new BigDecimal("0.20"));
        pricing.snapshot(job);

        JobAssignment assignment = new JobAssignment();
        pricing.copy(job, assignment);

        assertEquals("2026-09-v1", job.getPricingVersion());
        assertEquals("TRY", job.getCurrency());
        assertEquals(new BigDecimal("1000.00"), assignment.getWorkerPayout());
        assertEquals(new BigDecimal("0.075"), assignment.getCommissionRate());
        assertEquals(new BigDecimal("75.00"), assignment.getCommissionAmount());
        assertEquals(new BigDecimal("1075.00"), assignment.getTotalCharged());
        assertEquals(job.getPricingVersion(), assignment.getPricingVersion());
        assertEquals(job.getCurrency(), assignment.getCurrency());
    }

    @Test
    void quoteUsesDecimalStringsSoMobileNeverRecalculatesMoneyWithDouble() {
        CommissionProperties properties = new CommissionProperties();
        properties.setRate(new BigDecimal("0.075"));
        PricingManager pricing = new PricingManager(new CommissionCalculator(properties));

        var quote = pricing.quote(new BigDecimal("3.33"));

        assertEquals("3.33", quote.workerPayout());
        assertEquals("0.075", quote.serviceFeeRate());
        assertEquals("0.25", quote.serviceFeeAmount());
        assertEquals("3.58", quote.employerTotal());
        assertEquals("TRY", quote.currency());
    }
}
