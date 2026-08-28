package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.job.request.CreateCompanyJobDto;
import com.exskylab.koala.core.dtos.job.request.CreateIndividualJobDto;
import com.exskylab.koala.core.dtos.job.response.CompanyJobDto;
import com.exskylab.koala.core.dtos.job.response.IndividualJobDto;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.request.JobSearchCriteriaDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.entities.Job;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface JobService {
    IndividualJobDto createIndividualJob(CreateIndividualJobDto usersMeJobsPostRequestDto);

    CompanyJobDto createCorporateJob(UUID companyId, CreateCompanyJobDto companiesCompanyIdJobsPostRequestDto);

    Job getById(UUID jobId);

    Job getDetailedById(UUID jobId);

    PageDto<JobSummaryDto> search(JobSearchCriteriaDto criteria, Pageable pageable);

    PageDto<JobSummaryDto> getJobsOfCurrentUser(Pageable pageable);

    void closeJob(UUID jobId);

    boolean existsById(UUID jobId);

}
