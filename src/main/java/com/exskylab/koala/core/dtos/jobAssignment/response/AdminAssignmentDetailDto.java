package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.core.dtos.common.LinkedRecordDto;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.core.dtos.timesheet.response.WorkSessionDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAssignmentDetailDto(UUID id, UUID jobId, String jobTitle, UUID companyId, String companyName,
                                       JobEmployerType employerType,
                                       SafeUserDto worker, SafeUserDto employer,
                                       AssignmentStatus status, PaymentStatus paymentStatus,
                                       Instant assignmentDateTime, Instant expectedCompletionDateTime,
                                       Instant actualCompletionDateTime,
                                       int workHours, String notes, PricingBreakdownDto pricing,
                                       AdminApplicationDto application,
                                       List<WorkSessionDto> workSessions,
                                       AdminCancellationDto cancellation,
                                       List<AdminReviewDto> reviews,
                                       List<LinkedRecordDto> payments, LinkedRecordDto payout,
                                       List<LinkedRecordDto> disputes, List<LinkedRecordDto> reports,
                                       AssignmentTransportDto transport,
                                       List<AdminTimelineEventDto> timeline,
                                       Instant createdAt, Instant updatedAt) {
}
