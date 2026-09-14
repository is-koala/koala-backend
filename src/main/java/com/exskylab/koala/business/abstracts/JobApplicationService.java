package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.response.ReceivedApplicationDto;

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

    boolean hasApplicationForEmployer(UUID employerId, UUID workerId);
    JobApplicationDto applyToJob(UUID jobId, CreateJobApplicationDto jobsJobIdApplicationsPostRequestDto);

    List<JobApplicantDto> getJobApplicationsByIdJobId(UUID jobId);

    JobApplicationDetailDto respondToApplication(UUID applicationId, AnswerApplicationDto jobApplicationsApplicationIdPatchRequestDto);

    JobApplicationDetailDto withdrawApplication(UUID applicationId);

    PageDto<MyApplicationDto> getMyApplications(ApplicationStatus status, Pageable pageable);

    PageDto<ReceivedApplicationDto> getReceivedApplications(ApplicationStatus status, UUID jobId, UUID companyId,
                                                            Pageable pageable);

    long countForApplicant(UUID applicantId);
}
