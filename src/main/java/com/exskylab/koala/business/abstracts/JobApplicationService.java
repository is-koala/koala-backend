package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.jobApplication.request.AnswerApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.request.CreateJobApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDetailDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicantDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDto;
import com.exskylab.koala.entities.JobApplication;

import java.util.List;

import com.exskylab.koala.entities.ApplicationStatus;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface JobApplicationService {
    JobApplicationDto applyToJob(UUID jobId, CreateJobApplicationDto jobsJobIdApplicationsPostRequestDto);

    List<JobApplicantDto> getJobApplicationsByIdJobId(UUID jobId);

    JobApplicationDetailDto respondToApplication(UUID applicationId, AnswerApplicationDto jobApplicationsApplicationIdPatchRequestDto);

    Page<JobApplication> getMyApplications(ApplicationStatus status, Pageable pageable);

    boolean hasApplied(UUID jobId, UUID userId);
}
