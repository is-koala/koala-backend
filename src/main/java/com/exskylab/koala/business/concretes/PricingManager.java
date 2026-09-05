package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PricingService;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PricingManager implements PricingService {

    public static final String PRICING_VERSION = "2026-09-v1";
    public static final String CURRENCY = "TRY";

    private final CommissionCalculator commissionCalculator;

    public PricingManager(CommissionCalculator commissionCalculator) {
        this.commissionCalculator = commissionCalculator;
    }

    @Override
    public PricingBreakdownDto quote(BigDecimal workerPayout) {
        return describe(commissionCalculator.calculate(workerPayout));
    }

    @Override
    public void snapshot(Job job) {
        if (job.getPricingVersion() != null) {
            describe(job);
            return;
        }

        CommissionCalculator.Breakdown breakdown = commissionCalculator.calculate(job.getSalary());
        job.setPricingVersion(PRICING_VERSION);
        job.setCurrency(CURRENCY);
        job.setWorkerPayout(breakdown.workerPayout());
        job.setCommissionRate(breakdown.rate());
        job.setCommissionAmount(breakdown.commission());
        job.setTotalCharged(breakdown.totalCharged());
    }

    @Override
    public void copy(Job job, JobAssignment assignment) {
        if (job.getPricingVersion() == null) {
            throw new IllegalStateException("Published job has no pricing snapshot");
        }
        describe(job);

        assignment.setPricingVersion(job.getPricingVersion());
        assignment.setCurrency(job.getCurrency());
        assignment.setWorkerPayout(job.getWorkerPayout());
        assignment.setCommissionRate(job.getCommissionRate());
        assignment.setCommissionAmount(job.getCommissionAmount());
        assignment.setTotalCharged(job.getTotalCharged());
    }

    @Override
    public PricingBreakdownDto describe(Job job) {
        return describe(job.getPricingVersion(), job.getCurrency(), job.getWorkerPayout(),
                job.getCommissionRate(), job.getCommissionAmount(), job.getTotalCharged());
    }

    @Override
    public PricingBreakdownDto describe(JobAssignment assignment) {
        return describe(assignment.getPricingVersion(), assignment.getCurrency(), assignment.getWorkerPayout(),
                assignment.getCommissionRate(), assignment.getCommissionAmount(), assignment.getTotalCharged());
    }

    @Override
    public CommissionCalculator.Breakdown breakdown(JobAssignment assignment) {
        describe(assignment);
        return new CommissionCalculator.Breakdown(
                assignment.getWorkerPayout(),
                assignment.getCommissionAmount(),
                assignment.getTotalCharged(),
                assignment.getCommissionRate());
    }

    private PricingBreakdownDto describe(CommissionCalculator.Breakdown breakdown) {
        return describe(PRICING_VERSION, CURRENCY, breakdown.workerPayout(), breakdown.rate(),
                breakdown.commission(), breakdown.totalCharged());
    }

    private PricingBreakdownDto describe(String version, String currency, BigDecimal workerPayout,
                                         BigDecimal rate, BigDecimal fee, BigDecimal total) {
        return PricingBreakdownDto.from(version, currency, workerPayout, rate, fee, total);
    }
}
