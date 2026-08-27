package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.job.request.CompaniesCompanyIdJobsPostRequestDto;
import com.exskylab.koala.core.dtos.job.request.UsersMeJobsPostRequestDto;
import com.exskylab.koala.core.dtos.job.response.CompaniesCompanyIdJobsPostResponseDto;
import com.exskylab.koala.core.dtos.job.response.UsersMeJobsPostResponseDto;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.request.JobSearchCriteria;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.entities.Job;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface JobService {
    UsersMeJobsPostResponseDto createIndividualJob(UsersMeJobsPostRequestDto usersMeJobsPostRequestDto);

    CompaniesCompanyIdJobsPostResponseDto createCorporateJob(String companyId, CompaniesCompanyIdJobsPostRequestDto companiesCompanyIdJobsPostRequestDto);

    Job getById(String jobId);

    Job getDetailedById(UUID jobId);

    PageDto<JobSummaryDto> search(JobSearchCriteria criteria, Pageable pageable);

    PageDto<JobSummaryDto> getJobsOfCurrentUser(Pageable pageable);

    void closeJob(UUID jobId);

    boolean existsById(String jobId);

}
