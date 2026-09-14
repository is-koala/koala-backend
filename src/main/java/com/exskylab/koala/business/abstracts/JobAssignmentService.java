package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDto;

import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;

import com.exskylab.koala.core.dtos.jobAssignment.request.AnswerAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.PaymentSessionDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.JobAssignmentDto;
import com.exskylab.koala.entities.JobAssignment;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

public interface JobAssignmentService {
    JobAssignment save(JobAssignment jobAssignment);

    JobAssignmentDto answerToAssignment(UUID assignmentId, AnswerAssignmentDto jobAssignmentsAssignmentIdStatusPatchRequestDto);

    PaymentSessionDto initiatePaymentSession(UUID assignmentId, String clientIpAddress);

    PageDto<MyAssignmentDto> getMyAssignments(boolean asEmployer, Pageable pageable);

    MyAssignmentDto getMyAssignment(UUID assignmentId);

    JobAssignment completeAssignment(UUID assignmentId);

    List<UUID> findAutoCompletableAssignmentIds();

    List<UUID> findUnpaidExpiringAssignmentIds();

    boolean autoCompleteAssignment(UUID assignmentId);

    JobAssignment getDetailedById(UUID assignmentId);

    JobAssignment getDetailedByIdForUpdate(UUID assignmentId);

    AdminAssignmentDto getAdminDetail(UUID assignmentId);

    Page<AdminAssignmentDto> listForStaff(Pageable pageable);

    long countCompletedForWorker(UUID workerId);

    long countCompletedForEmployer(UUID employerId);

    boolean hasMoneyInEscrowFor(UUID workerId);

    boolean hasActiveWorkFor(UUID workerId);

    long countOccupiedSlots(UUID jobId);

    Optional<JobAssignment> findLatestByJobAndWorker(UUID jobId, UUID workerId);

    List<JobAssignment> findLatestByJobsAndWorkers(Collection<UUID> jobIds, Collection<UUID> workerIds);
}
