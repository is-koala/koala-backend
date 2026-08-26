package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.JobApplicationMessages;
import com.exskylab.koala.core.dtos.jobApplication.request.JobApplicationsApplicationIdPatchRequestDto;
import com.exskylab.koala.core.dtos.jobApplication.request.JobsJobIdApplicationsPostRequestDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationsApplicationIdPatchResponseDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobsJobIdApplicationsGetResponseDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobsJobIdApplicationsPostResponseDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.UserNotAssosiatedWithCompanyException;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class JobApplicationManager implements JobApplicationService {

    private final JobApplicationDao jobApplicationDao;
    private final JobService jobService;
    private final CompanyContactService companyContactService;
    private final SecurityService securityService;
    private final JobAssignmentService jobAssignmentService;

    private final Logger logger = LoggerFactory.getLogger(JobApplicationManager.class);
    private final JobApplicationMapper jobApplicationMapper;


    public JobApplicationManager(JobApplicationDao jobApplicationDao, JobService jobService,
                                 SecurityService securityService, CompanyContactService companyContactService,
                                 JobAssignmentService jobAssignmentService, JobApplicationMapper jobApplicationMapper) {
        this.jobApplicationDao = jobApplicationDao;
        this.jobService = jobService;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.jobAssignmentService = jobAssignmentService;
        this.jobApplicationMapper = jobApplicationMapper;
    }

    @Override
    @Transactional
    public JobsJobIdApplicationsPostResponseDto applyToJob(String jobId, JobsJobIdApplicationsPostRequestDto jobsJobIdApplicationsPostRequestDto) {
        logger.info("Applying to job with id: {}", jobId);
        var job = jobService.getById(jobId);

        User authenticatedUser = securityService.getAuthenticatedUserFromContext();
        logger.info("Applying to job with id: {} for userId: {}", jobId, authenticatedUser.getId());

        if (job.getCreator().getId().equals(authenticatedUser.getId())) {
            throw new IllegalArgumentException(JobApplicationMessages.USER_CANNOT_APPLY_TO_OWN_JOB);
        }

        if (jobApplicationDao.existsByJobIdAndUserId(UUID.fromString(jobId), authenticatedUser.getId())) {
            throw new IllegalArgumentException(JobApplicationMessages.USER_ALREADY_APPLIED_TO_JOB);
        }

        if (job.getEmployerType().equals(JobEmployerType.COMPANY)){
            if (companyContactService.isUserAContactOfCompany(job.getCompany().getId(), authenticatedUser.getId())) {
                throw new IllegalArgumentException(JobApplicationMessages.CONTACT_USER_CANNOT_APPLY_TO_COMPANY_JOB);
            }
        }

        JobApplication jobApplication = new JobApplication();
        jobApplication.setJob(job);
        jobApplication.setUser(authenticatedUser);
        jobApplication.setNotes(jobsJobIdApplicationsPostRequestDto.getNotes());
        jobApplication.setStatus(ApplicationStatus.APPLIED);
        jobApplication.setApplicationDateTime(LocalDateTime.now());
        jobApplication.setAssignment(null);

        JobApplication savedJobApplication = jobApplicationDao.save(jobApplication);
        logger.info("Saved job application with id: {}", jobApplication.getId());

        return jobApplicationMapper.toJobsJobIdApplicationsPostResponseDto(savedJobApplication);

    }

    @Override
    public List<JobsJobIdApplicationsGetResponseDto> getJobApplicationsByIdJobId(String jobId) {
        logger.info("Fetching job applications for job id: {}", jobId);
        Job job = jobService.getById(jobId);

        if (job.getEmployerType().equals(JobEmployerType.COMPANY)) {
            if (!companyContactService.isUserAContactOfCompany(job.getCompany().getId(), securityService.getAuthenticatedUserFromContext().getId())) {
                throw new IllegalArgumentException(JobApplicationMessages.USER_NOT_CONTACT_OF_COMPANY_TO_VIEW_APPLICATIONS);
            }
        } else if (job.getEmployerType().equals(JobEmployerType.INDIVIDUAL)) {
            if (!job.getCreator().getId().equals(securityService.getAuthenticatedUserFromContext().getId())) {
                throw new ResourceNotFoundException(JobApplicationMessages.USER_NOT_CREATOR_OF_INDIVIDUAL_JOB_TO_VIEW_APPLICATIONS);
            }
        }

        List<JobApplication> jobApplications = jobApplicationDao.findAllByJobId(UUID.fromString(jobId));
        logger.info("Found {} applications for job id: {}", jobApplications.size(), jobId);
        return jobApplicationMapper.toJobsJobIdApplicationsGetResponseDtoList(jobApplications);
    }

    @Override
    public JobApplicationsApplicationIdPatchResponseDto respondToApplication(String applicationId, JobApplicationsApplicationIdPatchRequestDto jobApplicationsApplicationIdPatchRequestDto) {
        logger.info("Responding to application with id: {}", applicationId);


        ApplicationStatus status;
        try{
            status = ApplicationStatus.valueOf(jobApplicationsApplicationIdPatchRequestDto.getStatus());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid status value: {}", jobApplicationsApplicationIdPatchRequestDto.getStatus());
            throw new IllegalArgumentException(JobApplicationMessages.INVALID_APPLICATION_STATUS);
        }

        JobApplication jobApplication = jobApplicationDao.findById(UUID.fromString(applicationId)).orElseThrow(() -> {
            logger.error("Job application with id: {} not found", applicationId);
            return new ResourceNotFoundException(JobApplicationMessages.JOB_APPLICATION_NOT_FOUND);
        });


        if (jobApplication.getStatus() == ApplicationStatus.ACCEPTED || jobApplication.getStatus() == ApplicationStatus.REJECTED) {
            logger.error("Job application with id: {} has already been responded to", applicationId);
            throw new IllegalArgumentException(JobApplicationMessages.JOB_APPLICATION_ALREADY_RESPONDED);
        }

        assertCurrentUserIsEmployerOf(jobApplication.getJob());


        jobApplication.setStatus(status);
        jobApplication.setAnsweredDateTime(LocalDateTime.now());
        JobAssignment assignmentToReturn = null;

        if (status.equals(ApplicationStatus.ACCEPTED)){
            JobAssignment jobAssignment = new JobAssignment();
            jobAssignment.setJob(jobApplication.getJob());
            jobAssignment.setWorker(jobApplication.getUser());
            jobAssignment.setApplication(jobApplication);
            jobAssignment.setStatus(AssignmentStatus.PENDING_CONFIRMATION);
            jobAssignment.setPaymentStatus(null);

            assignmentToReturn = jobAssignmentService.save(jobAssignment);
            logger.info("Job application with id: {} accepted", applicationId);

        }else if (status.equals(ApplicationStatus.REJECTED)){
            jobApplication.setRejectionReason(jobApplicationsApplicationIdPatchRequestDto.getRejectionReason());
            logger.info("Job application with id: {} rejected", applicationId);

        } else {
            logger.error("Invalid status transition attempted: {}", status);
            throw new IllegalArgumentException(JobApplicationMessages.INVALID_STATUS_TRANSITION);
        }

        jobApplication.setAssignment(assignmentToReturn);

        jobApplicationDao.save(jobApplication);
        logger.info("Saved job application with id: {}", jobApplication.getId());

        return jobApplicationMapper.toJobApplicationsApplicationIdPatchResponseDto(jobApplication);

    }

    private void assertCurrentUserIsEmployerOf(Job job) {
        User authenticatedUser = securityService.getAuthenticatedUserFromContext();

        if (job.getEmployerType() == JobEmployerType.COMPANY) {
            if (!companyContactService.isUserAContactOfCompany(job.getCompany().getId(), authenticatedUser.getId())) {
                logger.warn("User with id: {} is not a contact of company with id: {}",
                        authenticatedUser.getId(), job.getCompany().getId());
                throw new UserNotAssosiatedWithCompanyException(JobApplicationMessages.USER_NOT_CONTACT_OF_COMPANY_TO_VIEW_APPLICATIONS);
            }
            return;
        }

        if (!job.getCreator().getId().equals(authenticatedUser.getId())) {
            logger.warn("User with id: {} is not the creator of job with id: {}",
                    authenticatedUser.getId(), job.getId());
            throw new ResourceNotFoundException(JobApplicationMessages.USER_NOT_CREATOR_OF_INDIVIDUAL_JOB_TO_VIEW_APPLICATIONS);
        }
    }
}
