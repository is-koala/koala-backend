package com.exskylab.koala.business.concretes;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.dtos.job.request.CreateCompanyJobDto;
import com.exskylab.koala.core.dtos.job.request.CreateIndividualJobDto;
import com.exskylab.koala.core.dtos.job.response.CompanyJobDto;
import com.exskylab.koala.core.dtos.job.response.IndividualJobDto;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.request.JobSearchCriteriaDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import org.springframework.web.multipart.MultipartFile;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.JobAddressVisibility;
import com.exskylab.koala.core.helpers.JobAvailability;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.JobAlertService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JobManager implements JobService {

    private final JobDao jobDao;
    private final CompanyService companyService;

    private final SecurityService securityService;

    private final JobCategoryService jobCategoryService;

    private final AddressService addressService;
    private final JobMapper jobMapper;
    private final PageableSanitizer pageableSanitizer;
    private final CompanyContactService companyContactService;
    private final JobAlertService jobAlertService;
    private final GalleryService galleryService;
    private final CompanyFollowService companyFollowService;
    private final JobApplicationDao jobApplicationDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final NotificationService notificationService;
    private final MessageResolver messageResolver;
    private final PricingService pricingService;
    private final JobAvailability jobAvailability;
    private final JobAddressVisibility addressVisibility = new JobAddressVisibility();

    public JobManager(JobDao jobDao, SecurityService securityService, JobCategoryService jobCategoryService, AddressService addressService, CompanyService companyService, JobMapper jobMapper,
                      PageableSanitizer pageableSanitizer, CompanyContactService companyContactService,
                      JobAlertService jobAlertService, GalleryService galleryService,
                      CompanyFollowService companyFollowService, JobApplicationDao jobApplicationDao,
                      JobAssignmentDao jobAssignmentDao, NotificationService notificationService,
                      MessageResolver messageResolver, PricingService pricingService,
                      JobAvailability jobAvailability) {
        this.jobDao = jobDao;
        this.securityService = securityService;
        this.jobCategoryService = jobCategoryService;
        this.addressService = addressService;
        this.companyService = companyService;
        this.jobMapper = jobMapper;
        this.pageableSanitizer = pageableSanitizer;
        this.companyContactService = companyContactService;
        this.jobAlertService = jobAlertService;
        this.galleryService = galleryService;
        this.companyFollowService = companyFollowService;
        this.jobApplicationDao = jobApplicationDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.notificationService = notificationService;
        this.messageResolver = messageResolver;
        this.pricingService = pricingService;
        this.jobAvailability = jobAvailability;
    }

    @Override
    @Transactional
    public IndividualJobDto createIndividualJob(CreateIndividualJobDto usersMeJobsPostRequestDto) {
        log.debug("Creating individual job");
        User authenticatedUser = securityService.getAuthenticatedUser();
        log.info("Creating individual job for userId: {}", authenticatedUser.getId());

        if (!authenticatedUser.isEmployer()){
            log.error("User with id: {} is not an employer. Cannot create job.", authenticatedUser.getId());
            throw new ForbiddenException(JobMessages.USER_IS_NOT_EMPLOYER);
        }

        if (!authenticatedUser.isIdentityVerified()) {
            log.info("User {} cannot open an individual job yet: identity is not verified.",
                    authenticatedUser.getId());
            throw new ValidationException(JobMessages.INDIVIDUAL_EMPLOYER_IDENTITY_NOT_VERIFIED);
        }

        JobType type;
        try {
            type = JobType.valueOf(usersMeJobsPostRequestDto.getType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.error("Invalid job type: {}", usersMeJobsPostRequestDto.getType(), e);
            throw new ValidationException(JobMessages.INVALID_JOB_TYPE);
        }

        SalaryType salaryType;
        try {
            salaryType = SalaryType.valueOf(usersMeJobsPostRequestDto.getSalaryType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.error("Invalid salary type: {}", usersMeJobsPostRequestDto.getSalaryType(), e);
            throw new ValidationException(JobMessages.INVALID_SALARY_TYPE);
        }

        var jobCategory = jobCategoryService.getJobCategoryById(UUID.fromString(usersMeJobsPostRequestDto.getCategoryId()));

        if (jobCategory.isRequiresInsurance()){
            log.error("Job category with id: {} requires insurance. Cannot create individual job.", jobCategory.getId());
            throw new ValidationException(JobMessages.INDIVIDUAL_JOB_CANNOT_REQUIRE_INSURANCE);
        }

        Address address = addressService.createAddress(usersMeJobsPostRequestDto.getAddress());
        log.info("Created address for job with id: {}", address.getId());


        Job job = new Job();
        job.setCompany(null);
        job.setCreator(authenticatedUser);
        job.setCategory(jobCategory);
        job.setEmployerType(JobEmployerType.INDIVIDUAL);
        job.setTitle(usersMeJobsPostRequestDto.getTitle());
        job.setDescription(usersMeJobsPostRequestDto.getDescription());
        job.setSector(usersMeJobsPostRequestDto.getSector());
        job.setPosition(usersMeJobsPostRequestDto.getPosition());
        job.setDuties(usersMeJobsPostRequestDto.getDuties());
        job.setInsuranceRequired(false);
        job.setAddress(address);
        job.setAddressPublic(false);
        job.setType(type);
        job.setStartDateTime(usersMeJobsPostRequestDto.getStartDateTime());
        job.setEndDateTime(usersMeJobsPostRequestDto.getEndDateTime());
        job.setSalaryType(salaryType);
        job.setSalary(usersMeJobsPostRequestDto.getSalary());
        job.setMinAge(usersMeJobsPostRequestDto.getMinAge());
        job.setMaxAge(usersMeJobsPostRequestDto.getMaxAge());
        job.setExperienceRequired(usersMeJobsPostRequestDto.getExperienceRequired());
        job.setDressCode(usersMeJobsPostRequestDto.getDressCode());
        job.setHeadcount(usersMeJobsPostRequestDto.getHeadcount());
        job.setStatus(JobStatus.OPEN);
        pricingService.snapshot(job);

        Job savedJob = jobDao.save(job);
        jobAlertService.notifyMatching(savedJob);
        companyFollowService.notifyFollowers(savedJob);
        log.info("Saved individual job with id: {}", savedJob.getId());

        return jobMapper.toUsersMeJobsPostResponseDto(savedJob);

    }

    @Override
    @Transactional
    public CompanyJobDto createCorporateJob(UUID companyId, CreateCompanyJobDto companiesCompanyIdJobsPostRequestDto) {
        log.debug("Creating corporate job");
        User authenticatedUser = securityService.getAuthenticatedUser();
        log.info("Creating corporate job for userId: {}", authenticatedUser.getId());

        JobType type;
        try {
            type = JobType.valueOf(companiesCompanyIdJobsPostRequestDto.getType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.error("Invalid job type: {}", companiesCompanyIdJobsPostRequestDto.getType(), e);
            throw new ValidationException(JobMessages.INVALID_JOB_TYPE);
        }


        SalaryType salaryType;
        try {
            salaryType = SalaryType.valueOf(companiesCompanyIdJobsPostRequestDto.getSalaryType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.error("Invalid salary type: {}", companiesCompanyIdJobsPostRequestDto.getSalaryType(), e);
            throw new ValidationException(JobMessages.INVALID_SALARY_TYPE);
        }

        var jobCategory = jobCategoryService.getJobCategoryById(UUID.fromString(companiesCompanyIdJobsPostRequestDto.getCategoryId()));

        if (jobCategory.isRequiresInsurance() && !Boolean.TRUE.equals(companiesCompanyIdJobsPostRequestDto.getInsuranceRequired())){
            log.error("Job category with id: {} requires insurance but insuranceRequired is false. Cannot create job.", jobCategory.getId());
            throw new ValidationException(JobMessages.JOB_CATEGORY_REQUIRES_INSURANCE);
        }

        Address address = addressService.createAddress(companiesCompanyIdJobsPostRequestDto.getAddress());
        log.info("Created address for job with id: {}", address.getId());

        Company company = companyService.getCompanyById(companyId);

        boolean isUserContactOfCompany = company.getContacts().stream()
                .anyMatch(contact -> contact.getUser().getId().equals(authenticatedUser.getId()));
        if (!isUserContactOfCompany){
            log.error("User with id: {} is not a contact of company with id: {}. Cannot create job.", authenticatedUser.getId(), company.getId());
            throw new ValidationException(JobMessages.USER_IS_NOT_A_CONTACT_OF_COMPANY);
        }

        Job job = new Job();
        job.setCompany(company);
        job.setCreator(authenticatedUser);
        job.setCategory(jobCategory);
        job.setEmployerType(JobEmployerType.COMPANY);
        job.setTitle(companiesCompanyIdJobsPostRequestDto.getTitle());
        job.setDescription(companiesCompanyIdJobsPostRequestDto.getDescription());
        job.setSector(companiesCompanyIdJobsPostRequestDto.getSector());
        job.setPosition(companiesCompanyIdJobsPostRequestDto.getPosition());
        job.setDuties(companiesCompanyIdJobsPostRequestDto.getDuties());
        job.setInsuranceRequired(companiesCompanyIdJobsPostRequestDto.getInsuranceRequired());
        job.setAddress(address);
        job.setAddressPublic(companiesCompanyIdJobsPostRequestDto.isAddressPublic());
        job.setType(type);
        job.setStartDateTime(companiesCompanyIdJobsPostRequestDto.getStartDateTime());
        job.setEndDateTime(companiesCompanyIdJobsPostRequestDto.getEndDateTime());
        job.setSalaryType(salaryType);
        job.setSalary(companiesCompanyIdJobsPostRequestDto.getSalary());
        job.setMinAge(companiesCompanyIdJobsPostRequestDto.getMinAge());
        job.setMaxAge(companiesCompanyIdJobsPostRequestDto.getMaxAge());
        job.setExperienceRequired(companiesCompanyIdJobsPostRequestDto.getExperienceRequired());
        job.setDressCode(companiesCompanyIdJobsPostRequestDto.getDressCode());
        job.setHeadcount(companiesCompanyIdJobsPostRequestDto.getHeadcount());
        job.setStatus(JobStatus.OPEN);
        pricingService.snapshot(job);


        Job savedJob = jobDao.save(job);
        jobAlertService.notifyMatching(savedJob);
        companyFollowService.notifyFollowers(savedJob);
        log.info("Saved corporate job with id: {}", savedJob.getId());


        return jobMapper.toCompaniesCompanyIdJobsPostResponseDto(savedJob);

    }

    @Override
    public Job getById(UUID jobId) {
        log.debug("Getting job with id: {}", jobId);

        return jobDao.findById(jobId).orElseThrow(() -> {
            log.error("Job with id: {} not found.", jobId);
            return new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND);
        });
    }

    @Override
    public boolean existsById(UUID jobId) {
        log.debug("Checking existence of job {}", jobId);
        return jobDao.existsById(jobId);
    }

    private static final Set<AssignmentStatus> LIVE_ASSIGNMENT_STATUSES =
            java.util.EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION, AssignmentStatus.CONFIRMED,
                    AssignmentStatus.IN_PROGRESS);

    private static final Set<AssignmentStatus> HIRED_STATUSES =
            java.util.EnumSet.of(AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS,
                    AssignmentStatus.COMPLETED);

    private static final Set<AssignmentStatus> ADDRESS_VISIBLE_ASSIGNMENT_STATUSES =
            java.util.EnumSet.of(AssignmentStatus.PENDING_CONFIRMATION, AssignmentStatus.CONFIRMED,
                    AssignmentStatus.IN_PROGRESS);

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "salary", "startDateTime", "title");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Override
    @Transactional(readOnly = true)
    public JobDetailDto getJobDetail(UUID jobId, UUID viewerId) {
        Job job = getDetailedById(jobId);
        JobDetailDto detail = jobMapper.toJobDetailDto(job);
        detail.setHiredCount(jobAssignmentDao.countByJobIdAndStatusIn(jobId, HIRED_STATUSES));
        detail.setAcceptingApplications(jobAvailability.acceptsApplications(job));
        addressVisibility.protect(job, detail, viewerId, hasAddressAccess(jobId, viewerId));
        return detail;
    }

    @Override
    @Transactional(readOnly = true)
    public Job getDetailedById(UUID jobId) {
        return jobDao.findWithDetailsById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<JobSummaryDto> search(JobSearchCriteriaDto criteria, Pageable requested, UUID viewerId) {
        return listJobs(requested, viewerId, pageable -> {
            Page<UUID> ids = jobDao.searchIds(
                    JobStatus.OPEN,
                    jobAvailability.currentTime(),
                    blankToNull(criteria.getQuery()),
                    criteria.getCompanyId(),
                    criteria.getCategoryId(),
                    criteria.getCityId(),
                    criteria.getDistrictId(),
                    criteria.getType(),
                    criteria.getSalaryType(),
                    criteria.getEmployerType(),
                    criteria.getMinSalary(),
                    criteria.getMaxSalary(),
                    criteria.getInsuranceRequired(),
                    criteria.getExperienceRequired(),
                    criteria.getMinLatitude(),
                    criteria.getMaxLatitude(),
                    criteria.getMinLongitude(),
                    criteria.getMaxLongitude(),
                    pageable);

            return new PageImpl<>(inIdOrder(ids.getContent()), pageable, ids.getTotalElements());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<JobSummaryDto> getJobsOfCurrentUser(Pageable requested) {
        UUID viewerId = securityService.getAuthenticatedUserId();
        return listJobs(requested, viewerId, pageable -> jobDao.findAllByCreatorId(viewerId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<JobSummaryDto> getJobsOfCompany(UUID companyId, Pageable requested) {
        companyService.getCompanyById(companyId);

        UUID viewerId = securityService.getAuthenticatedUserId();
        if (!companyContactService.isUserAContactOfCompany(companyId, viewerId)) {
            log.warn("User {} is not a contact of company {}; company job listing refused", viewerId, companyId);
            throw new ForbiddenException(JobMessages.USER_IS_NOT_A_CONTACT_OF_COMPANY);
        }

        return listJobs(requested, viewerId, pageable -> jobDao.findAllByCompanyId(companyId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GalleryImageDto> getJobGallery(UUID jobId) {
        if (!jobDao.existsById(jobId)) {
            throw new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND);
        }
        return galleryService.getJobGallery(jobId);
    }

    @Override
    @Transactional
    public List<GalleryImageDto> addJobImage(UUID jobId, MultipartFile file) {
        return galleryService.addJobImage(requireJobEditor(jobId), file);
    }

    @Override
    @Transactional
    public List<GalleryImageDto> replaceJobGallery(UUID jobId, UpdateGalleryDto request) {
        return galleryService.replaceJobGallery(requireJobEditor(jobId), request);
    }

    @Override
    @Transactional
    public List<GalleryImageDto> removeJobImage(UUID jobId, UUID imageId) {
        return galleryService.removeJobImage(requireJobEditor(jobId), imageId);
    }

    @Override
    @Transactional
    public void cancelJob(UUID jobId) {
        Job job = requireJobEditor(jobId);

        if (job.getStatus() == JobStatus.CANCELLED) {
            throw new ConflictException(JobMessages.JOB_NOT_CANCELLABLE);
        }

        if (jobAssignmentDao.existsByJobIdAndStatusIn(jobId, LIVE_ASSIGNMENT_STATUSES)) {
            throw new ConflictException(JobMessages.JOB_HAS_LIVE_ASSIGNMENT);
        }

        job.setStatus(JobStatus.CANCELLED);
        jobDao.save(job);

        log.info("Job {} cancelled by user {}", jobId, securityService.getAuthenticatedUserId());
        notifyApplicantsOfCancellation(job);
    }

    private void notifyApplicantsOfCancellation(Job job) {
        List<JobApplication> applications = jobApplicationDao.findAllByJobId(job.getId());
        notificationService.preparePushRecipients(applications.stream()
                .map(application -> application.getUser().getId())
                .toList());

        for (JobApplication application : applications) {
            try {
                SendPushToUserDto push = new SendPushToUserDto();
                push.setRecipient(application.getUser());
                push.setTitle(job.getTitle());
                push.setBody(messageResolver.resolve(JobMessages.JOB_CANCELLED_NOTICE));
                push.setCategory(NotificationCategory.APPLICATION_UPDATES);

                notificationService.sendPushToUser(push, DispatchPriority.NORMAL, true);
            } catch (Exception exception) {
                log.warn("Applicant {} could not be notified about cancelled job {}: {}",
                        application.getUser().getId(), job.getId(), exception.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countJobsCreatedBy(UUID userId) {
        return jobDao.countByCreatorId(userId);
    }

    private Job requireJobEditor(UUID jobId) {
        Job job = jobDao.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND));

        UUID currentUserId = securityService.getAuthenticatedUserId();
        boolean isOwner = job.getCreator() != null && job.getCreator().getId().equals(currentUserId);
        boolean isCompanyContact = job.getCompany() != null
                && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), currentUserId);

        if (!isOwner && !isCompanyContact) {
            throw new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND);
        }
        return job;
    }

    @Override
    @Transactional
    public void closeJob(UUID jobId) {
        Job job = requireJobEditor(jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException(JobMessages.JOB_NOT_OPEN);
        }

        job.setStatus(JobStatus.CLOSED);
        jobDao.save(job);
        log.info("Job {} closed by user {}", jobId, securityService.getAuthenticatedUserId());
    }

    private PageDto<JobSummaryDto> listJobs(Pageable requested, UUID viewerId,
                                            Function<Pageable, Page<Job>> query) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE_FIELDS);
        Pageable pageable = pageableSanitizer.sanitize(requested, SORTABLE_FIELDS, DEFAULT_SORT);

        Page<Job> jobs = query.apply(pageable);
        Set<UUID> assignedJobIds =
                assignedJobIds(jobs.getContent().stream().map(Job::getId).toList(), viewerId);

        return enrich(PageDto.of(jobs, job -> {
            JobSummaryDto summary = jobMapper.toJobSummaryDto(job);
            addressVisibility.protect(job, summary, viewerId, assignedJobIds.contains(job.getId()));
            return summary;
        }));
    }

    private PageDto<JobSummaryDto> enrich(PageDto<JobSummaryDto> page) {
        List<JobSummaryDto> content = page.content();
        if (content.isEmpty()) {
            return page;
        }

        galleryService.fillJobCovers(content);

        List<UUID> ids = content.stream().map(JobSummaryDto::getId).toList();
        Map<UUID, Long> counts = jobApplicationDao.countByJobIds(ids).stream()
                .collect(Collectors.toMap(JobApplicationDao.JobApplicationCount::getJobId,
                        JobApplicationDao.JobApplicationCount::getTotal));

        Map<UUID, Long> hired = jobAssignmentDao.countHiredByJobIds(ids, HIRED_STATUSES).stream()
                .collect(Collectors.toMap(JobAssignmentDao.HiredCount::getJobId,
                        JobAssignmentDao.HiredCount::getTotal));

        content.forEach(job -> {
            job.setApplicationCount(counts.getOrDefault(job.getId(), 0L));
            job.setHiredCount(hired.getOrDefault(job.getId(), 0L));
        });
        return page;
    }

    private List<Job> inIdOrder(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Job> loaded = jobDao.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Job::getId, job -> job));
        return ids.stream().map(loaded::get).filter(Objects::nonNull).toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasAddressAccess(UUID jobId, UUID viewerId) {
        return viewerId != null && jobAssignmentDao.existsByJobIdAndWorkerIdAndStatusIn(
                jobId, viewerId, ADDRESS_VISIBLE_ASSIGNMENT_STATUSES);
    }

    private Set<UUID> assignedJobIds(List<UUID> jobIds, UUID viewerId) {
        return viewerId == null || jobIds.isEmpty() ? Set.of()
                : Set.copyOf(jobAssignmentDao.findVisibleAddressJobIds(
                        jobIds, viewerId, ADDRESS_VISIBLE_ASSIGNMENT_STATUSES));
    }

}
