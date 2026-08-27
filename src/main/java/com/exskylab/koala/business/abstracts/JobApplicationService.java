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

public interface JobApplicationService {
    JobApplicationDto applyToJob(String jobId, CreateJobApplicationDto jobsJobIdApplicationsPostRequestDto);

    List<JobApplicantDto> getJobApplicationsByIdJobId(String jobId);

    JobApplicationDetailDto respondToApplication(String applicationId, AnswerApplicationDto jobApplicationsApplicationIdPatchRequestDto);

    Page<JobApplication> getMyApplications(ApplicationStatus status, Pageable pageable);
}
