package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminAssignmentDto(UUID id, UUID jobId, String jobTitle, UUID companyId, String companyName,
                                 SafeUserDto worker, SafeUserDto employer,
                                 AssignmentStatus status, PaymentStatus paymentStatus,
                                 Instant assignmentDateTime, Instant actualCompletionDateTime,
                                 int workHours, PricingBreakdownDto pricing) {
}
