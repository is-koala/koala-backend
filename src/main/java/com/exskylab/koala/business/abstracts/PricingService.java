package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;

import java.math.BigDecimal;

public interface PricingService {

    PricingBreakdownDto quote(BigDecimal workerPayout);

    void snapshot(Job job);

    void copy(Job job, JobAssignment assignment);

    PricingBreakdownDto describe(Job job);

    PricingBreakdownDto describe(JobAssignment assignment);

    CommissionCalculator.Breakdown breakdown(JobAssignment assignment);
}
