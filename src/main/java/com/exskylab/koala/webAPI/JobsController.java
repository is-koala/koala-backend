package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.core.constants.JobApplicationMessages;
import com.exskylab.koala.core.dtos.jobApplication.request.CreateJobApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicantDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDto;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.request.JobSearchCriteriaDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.UUID;
import jakarta.validation.Valid;
import com.exskylab.koala.entities.JobApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/jobs")
public class JobsController {

    private final JobService jobService;
    private final JobApplicationService jobApplicationService;
    private final JobApplicationMapper jobApplicationMapper;
    private final JobMapper jobMapper;


    public JobsController(JobService jobService, JobApplicationService jobApplicationService, JobApplicationMapper jobApplicationMapper, JobMapper jobMapper) {
        this.jobService = jobService;
        this.jobApplicationService = jobApplicationService;
        this.jobApplicationMapper = jobApplicationMapper;
        this.jobMapper = jobMapper;
    }



    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<JobSummaryDto>>> searchJobs(
            JobSearchCriteriaDto criteria,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.search(criteria, pageable), JobMessages.JOBS_LISTED, HttpStatus.OK));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<SuccessDataResult<JobDetailDto>> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobMapper.toJobDetailDto(jobService.getDetailedById(jobId)),
                JobMessages.JOB_RETRIEVED, HttpStatus.OK));
    }

    @PostMapping("/{jobId}/close")
    public ResponseEntity<SuccessResult> closeJob(@PathVariable UUID jobId) {
        jobService.closeJob(jobId);
        return ResponseEntity.ok(new SuccessResult(JobMessages.JOB_CLOSED, HttpStatus.OK));
    }

    @PostMapping("/{jobId}/applications")
    public ResponseEntity<SuccessDataResult<JobApplicationDto>> applyToJob(@PathVariable UUID jobId, @RequestBody @Valid CreateJobApplicationDto jobsJobIdApplicationsPostResponseDto){

        JobApplicationDto jobApplicationsDto = jobApplicationService.applyToJob(jobId, jobsJobIdApplicationsPostResponseDto);


        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<JobApplicationDto>(
                jobApplicationsDto,
                JobApplicationMessages.JOB_APPLICATION_CREATED_SUCCESS,
                HttpStatus.CREATED
        ));

    }


    @GetMapping("/{jobId}/applications")
    public ResponseEntity<SuccessDataResult<List<JobApplicantDto>>> getJobApplicationById(@PathVariable UUID jobId){

        List<JobApplicantDto> jobApplicationsDto = jobApplicationService.getJobApplicationsByIdJobId(jobId);

        return ResponseEntity.ok(new SuccessDataResult<List<JobApplicantDto>>(
                jobApplicationsDto,
                JobApplicationMessages.JOB_APPLICATIONS_RETRIEVED_SUCCESS,
                HttpStatus.OK
        ));


    }


}
