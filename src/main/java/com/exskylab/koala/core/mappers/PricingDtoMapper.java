package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import org.springframework.stereotype.Component;

@Component
public class PricingDtoMapper {

    public PricingBreakdownDto toPricing(Job job) {
        if (job == null || job.getPricingVersion() == null) {
            return null;
        }
        return PricingBreakdownDto.from(job.getPricingVersion(), job.getCurrency(), job.getWorkerPayout(),
                job.getCommissionRate(), job.getCommissionAmount(), job.getTotalCharged());
    }

    public PricingBreakdownDto toPricing(JobAssignment assignment) {
        if (assignment == null || assignment.getPricingVersion() == null) {
            return null;
        }
        return PricingBreakdownDto.from(assignment.getPricingVersion(), assignment.getCurrency(),
                assignment.getWorkerPayout(), assignment.getCommissionRate(),
                assignment.getCommissionAmount(), assignment.getTotalCharged());
    }

    public PricingBreakdownDto toPricing(Payment payment) {
        if (payment == null || payment.getPricingVersion() == null) {
            return null;
        }
        return PricingBreakdownDto.from(payment.getPricingVersion(), payment.getCurrency(),
                payment.getWorkerPayout(), payment.getCommissionRate(),
                payment.getCommissionAmount(), payment.getTotalCharged());
    }
}
