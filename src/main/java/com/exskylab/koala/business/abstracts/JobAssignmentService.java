package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;

import com.exskylab.koala.core.dtos.jobAssignment.request.AnswerAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.PaymentSessionDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.JobAssignmentDto;
import com.exskylab.koala.entities.JobAssignment;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

public interface JobAssignmentService {
    JobAssignment save(JobAssignment jobAssignment);

    JobAssignmentDto answerToAssignment(UUID assignmentId, AnswerAssignmentDto jobAssignmentsAssignmentIdStatusPatchRequestDto);

    PaymentSessionDto initiatePaymentSession(UUID assignmentId, String clientIpAddress);

    PageDto<MyAssignmentDto> getMyAssignments(boolean asEmployer, Pageable pageable);

    JobAssignment completeAssignment(UUID assignmentId);

    List<UUID> findAutoCompletableAssignmentIds();

    boolean autoCompleteAssignment(UUID assignmentId);

    JobAssignment getDetailedById(UUID assignmentId);

    long countCompletedForWorker(UUID workerId);

    long countCompletedForEmployer(UUID employerId);

    boolean hasLiveAssignmentForJob(UUID jobId);

    boolean hasMoneyInEscrowFor(UUID workerId);

    boolean hasActiveWorkFor(UUID workerId);
}
