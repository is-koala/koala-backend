package com.exskylab.koala.business.concretes;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.JobApplicationMessages;
import com.exskylab.koala.core.dtos.jobApplication.request.AnswerApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.request.CreateJobApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDetailDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicantDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class JobApplicationManager implements JobApplicationService {

    private static final java.util.Set<String> MY_APPLICATIONS_SORTABLE =
            java.util.Set.of("applicationDateTime", "answeredDateTime", "status");


    private final JobApplicationDao jobApplicationDao;
    private final JobService jobService;
    private final CompanyContactService companyContactService;
    private final SecurityService securityService;
    private final JobAssignmentService jobAssignmentService;
    private final JobApplicationMapper jobApplicationMapper;
    private final PageableSanitizer pageableSanitizer;
    private final GalleryService galleryService;
    private final ActivityNotificationService activityNotificationService;
    private final BankAccountService bankAccountService;
    private final PricingService pricingService;


    public JobApplicationManager(JobApplicationDao jobApplicationDao, JobService jobService,
                                 SecurityService securityService, CompanyContactService companyContactService,
                                 JobAssignmentService jobAssignmentService, JobApplicationMapper jobApplicationMapper,
                                 PageableSanitizer pageableSanitizer, GalleryService galleryService,
                                 ActivityNotificationService activityNotificationService,
                                 BankAccountService bankAccountService,
                                 PricingService pricingService) {
        this.jobApplicationDao = jobApplicationDao;
        this.jobService = jobService;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.jobAssignmentService = jobAssignmentService;
        this.jobApplicationMapper = jobApplicationMapper;
        this.pageableSanitizer = pageableSanitizer;
        this.galleryService = galleryService;
        this.activityNotificationService = activityNotificationService;
        this.bankAccountService = bankAccountService;
        this.pricingService = pricingService;
    }

    @Override
    @Transactional
    public JobApplicationDto applyToJob(UUID jobId, CreateJobApplicationDto jobsJobIdApplicationsPostRequestDto) {
        log.info("Applying to job with id: {}", jobId);
        var job = jobService.getById(jobId);

        User authenticatedUser = securityService.getAuthenticatedUser();
        log.info("Applying to job with id: {} for userId: {}", jobId, authenticatedUser.getId());

        if (job.getCreator().getId().equals(authenticatedUser.getId())) {
            throw new ValidationException(JobApplicationMessages.USER_CANNOT_APPLY_TO_OWN_JOB);
        }

        if (jobApplicationDao.existsByJobIdAndUserId(jobId, authenticatedUser.getId())) {
            throw new ValidationException(JobApplicationMessages.USER_ALREADY_APPLIED_TO_JOB);
        }

        if (job.getEmployerType().equals(JobEmployerType.COMPANY)){
            if (companyContactService.isUserAContactOfCompany(job.getCompany().getId(), authenticatedUser.getId())) {
                throw new ValidationException(JobApplicationMessages.CONTACT_USER_CANNOT_APPLY_TO_COMPANY_JOB);
            }
        }

        assertReadyToWork(authenticatedUser);

        JobApplication jobApplication = new JobApplication();
        jobApplication.setJob(job);
        jobApplication.setUser(authenticatedUser);
        jobApplication.setNotes(jobsJobIdApplicationsPostRequestDto.getNotes());
        jobApplication.setStatus(ApplicationStatus.APPLIED);
        jobApplication.setApplicationDateTime(Instant.now());
        jobApplication.setAssignment(null);

        JobApplication savedJobApplication = jobApplicationDao.save(jobApplication);
        log.info("Saved job application with id: {}", jobApplication.getId());

        return jobApplicationMapper.toJobsJobIdApplicationsPostResponseDto(savedJobApplication);

    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicantDto> getJobApplicationsByIdJobId(UUID jobId) {
        log.debug("Fetching job applications for job id: {}", jobId);
        Job job = jobService.getById(jobId);

        if (job.getEmployerType().equals(JobEmployerType.COMPANY)) {
            if (!companyContactService.isUserAContactOfCompany(job.getCompany().getId(), securityService.getAuthenticatedUserId())) {
                throw new ValidationException(JobApplicationMessages.USER_NOT_CONTACT_OF_COMPANY_TO_VIEW_APPLICATIONS);
            }
        } else if (job.getEmployerType().equals(JobEmployerType.INDIVIDUAL)) {
            if (!job.getCreator().getId().equals(securityService.getAuthenticatedUserId())) {
                throw new ResourceNotFoundException(JobApplicationMessages.USER_NOT_CREATOR_OF_INDIVIDUAL_JOB_TO_VIEW_APPLICATIONS);
            }
        }

        List<JobApplication> jobApplications = jobApplicationDao.findAllByJobId(jobId);
        log.info("Found {} applications for job id: {}", jobApplications.size(), jobId);
        return jobApplicationMapper.toJobsJobIdApplicationsGetResponseDtoList(jobApplications);
    }

    @Override
    @Transactional
    public JobApplicationDetailDto respondToApplication(UUID applicationId, AnswerApplicationDto jobApplicationsApplicationIdPatchRequestDto) {
        log.info("Responding to application with id: {}", applicationId);


        ApplicationStatus status;
        try{
            status = ApplicationStatus.valueOf(jobApplicationsApplicationIdPatchRequestDto.getStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.error("Invalid status value: {}", jobApplicationsApplicationIdPatchRequestDto.getStatus());
            throw new ValidationException(JobApplicationMessages.INVALID_APPLICATION_STATUS);
        }

        JobApplication jobApplication = jobApplicationDao.findById(applicationId).orElseThrow(() -> {
            log.error("Job application with id: {} not found", applicationId);
            return new ResourceNotFoundException(JobApplicationMessages.JOB_APPLICATION_NOT_FOUND);
        });


        if (jobApplication.getStatus() == ApplicationStatus.ACCEPTED || jobApplication.getStatus() == ApplicationStatus.REJECTED) {
            log.error("Job application with id: {} has already been responded to", applicationId);
            throw new ValidationException(JobApplicationMessages.JOB_APPLICATION_ALREADY_RESPONDED);
        }

        assertCurrentUserIsEmployerOf(jobApplication.getJob());


        jobApplication.setStatus(status);
        jobApplication.setAnsweredDateTime(Instant.now());
        JobAssignment assignmentToReturn = null;

        if (status.equals(ApplicationStatus.ACCEPTED)){
            JobAssignment jobAssignment = new JobAssignment();
            jobAssignment.setJob(jobApplication.getJob());
            jobAssignment.setWorker(jobApplication.getUser());
            jobAssignment.setApplication(jobApplication);
            jobAssignment.setStatus(AssignmentStatus.PENDING_CONFIRMATION);
            jobAssignment.setPaymentStatus(null);
            pricingService.copy(jobApplication.getJob(), jobAssignment);

            assignmentToReturn = jobAssignmentService.save(jobAssignment);
            log.info("Job application with id: {} accepted", applicationId);

            activityNotificationService.assignmentOffered(assignmentToReturn);

        }else if (status.equals(ApplicationStatus.REJECTED)){
            jobApplication.setRejectionReason(jobApplicationsApplicationIdPatchRequestDto.getRejectionReason());
            log.info("Job application with id: {} rejected", applicationId);

        } else {
            log.error("Invalid status transition attempted: {}", status);
            throw new ValidationException(JobApplicationMessages.INVALID_STATUS_TRANSITION);
        }

        jobApplication.setAssignment(assignmentToReturn);

        jobApplicationDao.save(jobApplication);
        log.info("Saved job application with id: {}", jobApplication.getId());

        activityNotificationService.applicationAnswered(jobApplication);

        return jobApplicationMapper.toJobApplicationsApplicationIdPatchResponseDto(jobApplication);

    }

    private void assertReadyToWork(User applicant) {
        if (!applicant.isIdentityVerified()) {
            log.info("User {} cannot apply yet: identity is not verified.", applicant.getId());
            throw new ValidationException(JobApplicationMessages.APPLICANT_IDENTITY_NOT_VERIFIED);
        }

        if (applicant.getAddress() == null) {
            log.info("User {} cannot apply yet: no address on file.", applicant.getId());
            throw new ValidationException(JobApplicationMessages.APPLICANT_ADDRESS_REQUIRED);
        }

        boolean hasIban = bankAccountService.findPrimaryIban(applicant.getId())
                .filter(iban -> !iban.isBlank())
                .isPresent();

        if (!hasIban) {
            log.info("User {} cannot apply yet: no primary bank account.", applicant.getId());
            throw new ValidationException(JobApplicationMessages.APPLICANT_BANK_ACCOUNT_REQUIRED);
        }
    }

    private void assertCurrentUserIsEmployerOf(Job job) {
        User authenticatedUser = securityService.getAuthenticatedUser();

        if (job.getEmployerType() == JobEmployerType.COMPANY) {
            if (!companyContactService.isUserAContactOfCompany(job.getCompany().getId(), authenticatedUser.getId())) {
                log.warn("User with id: {} is not a contact of company with id: {}",
                        authenticatedUser.getId(), job.getCompany().getId());
                throw new ForbiddenException(JobApplicationMessages.USER_NOT_CONTACT_OF_COMPANY_TO_VIEW_APPLICATIONS);
            }
            return;
        }

        if (!job.getCreator().getId().equals(authenticatedUser.getId())) {
            log.warn("User with id: {} is not the creator of job with id: {}",
                    authenticatedUser.getId(), job.getId());
            throw new ResourceNotFoundException(JobApplicationMessages.USER_NOT_CREATOR_OF_INDIVIDUAL_JOB_TO_VIEW_APPLICATIONS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasApplicationForEmployer(UUID employerId, UUID workerId) {
        if (jobApplicationDao.existsByUserIdAndJobCreatorId(workerId, employerId)) {
            return true;
        }

        return jobApplicationDao.findCompanyIdsAppliedToBy(workerId).stream()
                .anyMatch(companyId -> companyContactService.isUserAContactOfCompany(companyId, employerId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<MyApplicationDto> getMyApplications(ApplicationStatus status, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, MY_APPLICATIONS_SORTABLE);
        Pageable pageable = pageableSanitizer.sanitize(requested, MY_APPLICATIONS_SORTABLE,
                Sort.by(Sort.Direction.DESC, "applicationDateTime"));

        UUID userId = securityService.getAuthenticatedUserId();
        Page<JobApplication> page = status == null
                ? jobApplicationDao.findAllByUserId(userId, pageable)
                : jobApplicationDao.findAllByUserIdAndStatus(userId, status, pageable);

        PageDto<MyApplicationDto> result = PageDto.of(page,
                jobApplicationMapper.toMyApplicationDtoList(page.getContent()));
        galleryService.fillJobCovers(result.content().stream().map(MyApplicationDto::job).toList());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasApplied(UUID jobId, UUID userId) {
        return jobApplicationDao.existsByJobIdAndUserId(jobId, userId);
    }
}
