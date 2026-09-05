package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MyAssignmentDto(UUID id, JobSummaryDto job, SafeUserDto worker,
                              AssignmentStatus status, PaymentStatus paymentStatus,
                              Instant assignmentDateTime, Instant actualCompletionDateTime,
                              int workHours, BigDecimal workerPayout, BigDecimal totalCharged,
                              PricingBreakdownDto pricing) {
}
