package com.exskylab.koala.core.dtos.myWork.response;

import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.entities.WorkItemKind;

import java.time.Instant;
import java.util.UUID;

public record WorkItemDto(WorkItemKind kind, UUID id, JobSummaryDto job, String status,
                          Instant occurredAt,
                          MyApplicationDto application, MyAssignmentDto assignment) {

    public static WorkItemDto of(MyApplicationDto application) {
        return new WorkItemDto(WorkItemKind.APPLICATION, application.id(), application.job(),
                application.status() == null ? null : application.status().name(),
                application.applicationDateTime(), application, null);
    }

    public static WorkItemDto of(MyAssignmentDto assignment) {
        return new WorkItemDto(WorkItemKind.ASSIGNMENT, assignment.id(), assignment.job(),
                assignment.status() == null ? null : assignment.status().name(),
                assignment.assignmentDateTime(), null, assignment);
    }
}
