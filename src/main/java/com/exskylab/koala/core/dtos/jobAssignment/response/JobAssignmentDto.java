package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.core.dtos.company.response.OnlyCompanyDto;
import com.exskylab.koala.core.dtos.job.response.ShortJobDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.MessagingAvailabilityReason;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobAssignmentDto {

    private UUID id;

    private Instant assignmentDateTime;

    private AssignmentStatus status;

    private ShortJobDto job;

    private SafeUserDto worker;

    private OnlyCompanyDto company;

    private boolean messagingAvailable;

    private MessagingAvailabilityReason messagingAvailableReason;

}
