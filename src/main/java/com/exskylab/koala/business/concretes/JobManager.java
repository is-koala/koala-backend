package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.dtos.job.request.CompaniesCompanyIdJobsPostRequestDto;
import com.exskylab.koala.core.dtos.job.request.UsersMeJobsPostRequestDto;
import com.exskylab.koala.core.dtos.job.response.CompaniesCompanyIdJobsPostResponseDto;
import com.exskylab.koala.core.dtos.job.response.UsersMeJobsPostResponseDto;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.request.JobSearchCriteria;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.exceptions.UserIsNotEmployerException;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JobManager implements JobService {

    private final JobDao jobDao;

    private final Logger logger = LoggerFactory.getLogger(JobManager.class);
    private final CompanyService companyService;

    private final SecurityService securityService;

    private final JobCategoryService jobCategoryService;

    private final AddressService addressService;
    private final JobMapper jobMapper;
    private final PageableSanitizer pageableSanitizer;
    private final CompanyContactService companyContactService;

    public JobManager(JobDao jobDao, SecurityService securityService, JobCategoryService jobCategoryService, AddressService addressService, CompanyService companyService, JobMapper jobMapper,
                      PageableSanitizer pageableSanitizer, CompanyContactService companyContactService) {
        this.jobDao = jobDao;
        this.securityService = securityService;
        this.jobCategoryService = jobCategoryService;
        this.addressService = addressService;
        this.companyService = companyService;
        this.jobMapper = jobMapper;
        this.pageableSanitizer = pageableSanitizer;
        this.companyContactService = companyContactService;
    }

    @Override
    @Transactional
    public UsersMeJobsPostResponseDto createIndividualJob(UsersMeJobsPostRequestDto usersMeJobsPostRequestDto) {
        logger.info("Creating individual job...");
        User authenticatedUser = securityService.getAuthenticatedUser();
        logger.info("Creating individual job for userId: {}", authenticatedUser.getId());

        if (!authenticatedUser.isEmployer()){
            logger.error("User with id: {} is not an employer. Cannot create job.", authenticatedUser.getId());
            throw new UserIsNotEmployerException(JobMessages.USER_IS_NOT_EMPLOYER);
        }

        JobType type;
        try {
            type = JobType.valueOf(usersMeJobsPostRequestDto.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid job type: {}", usersMeJobsPostRequestDto.getType());
            throw new IllegalArgumentException(JobMessages.INVALID_JOB_TYPE);
        }

        SalaryType salaryType;
        try {
            salaryType = SalaryType.valueOf(usersMeJobsPostRequestDto.getSalaryType().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid salary type: {}", usersMeJobsPostRequestDto.getSalaryType());
            throw new IllegalArgumentException(JobMessages.INVALID_SALARY_TYPE);
        }

        var jobCategory = jobCategoryService.getJobCategoryById(UUID.fromString(usersMeJobsPostRequestDto.getCategoryId()));

        if (jobCategory.isRequiresInsurance()){
            logger.error("Job category with id: {} requires insurance. Cannot create individual job.", jobCategory.getId());
            throw new IllegalArgumentException(JobMessages.INDIVIDUAL_JOB_CANNOT_REQUIRE_INSURANCE);
        }

        Address address = addressService.createAddress(usersMeJobsPostRequestDto.getAddress());
        logger.info("Created address for job with id: {}", address.getId());


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
        job.setType(type);
        job.setStartDateTime(usersMeJobsPostRequestDto.getStartDateTime());
        job.setEndDateTime(usersMeJobsPostRequestDto.getEndDateTime());
        job.setSalaryType(salaryType);
        job.setSalary(usersMeJobsPostRequestDto.getSalary());
        job.setMinAge(usersMeJobsPostRequestDto.getMinAge());
        job.setMaxAge(usersMeJobsPostRequestDto.getMaxAge());
        job.setExperienceRequired(usersMeJobsPostRequestDto.isExperienceRequired());
        job.setDressCode(usersMeJobsPostRequestDto.getDressCode());
        job.setStatus(JobStatus.OPEN);

        Job savedJob = jobDao.save(job);
        logger.info("Saved individual job with id: {}", savedJob.getId());

        return jobMapper.toUsersMeJobsPostResponseDto(savedJob);

    }

    @Override
    @Transactional
    public CompaniesCompanyIdJobsPostResponseDto createCorporateJob(String companyId, CompaniesCompanyIdJobsPostRequestDto companiesCompanyIdJobsPostRequestDto) {
        logger.info("Creating corporate job...");
        User authenticatedUser = securityService.getAuthenticatedUser();
        logger.info("Creating corporate job for userId: {}", authenticatedUser.getId());

        JobType type;
        try {
            type = JobType.valueOf(companiesCompanyIdJobsPostRequestDto.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid job type: {}", companiesCompanyIdJobsPostRequestDto.getType());
            throw new IllegalArgumentException(JobMessages.INVALID_JOB_TYPE);
        }


        SalaryType salaryType;
        try {
            salaryType = SalaryType.valueOf(companiesCompanyIdJobsPostRequestDto.getSalaryType().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid salary type: {}", companiesCompanyIdJobsPostRequestDto.getSalaryType());
            throw new IllegalArgumentException(JobMessages.INVALID_SALARY_TYPE);
        }

        var jobCategory = jobCategoryService.getJobCategoryById(UUID.fromString(companiesCompanyIdJobsPostRequestDto.getCategoryId()));

        if (jobCategory.isRequiresInsurance() && !companiesCompanyIdJobsPostRequestDto.isInsuranceRequired()){
            logger.error("Job category with id: {} requires insurance but insuranceRequired is false. Cannot create job.", jobCategory.getId());
            throw new IllegalArgumentException(JobMessages.JOB_CATEGORY_REQUIRES_INSURANCE);
        }

        Address address = addressService.createAddress(companiesCompanyIdJobsPostRequestDto.getAddress());
        logger.info("Created address for job with id: {}", address.getId());

        Company company = companyService.getCompanyById(UUID.fromString(companyId));

        boolean isUserContactOfCompany = company.getContacts().stream()
                .anyMatch(contact -> contact.getUser().getId().equals(authenticatedUser.getId()));
        if (!isUserContactOfCompany){
            logger.error("User with id: {} is not a contact of company with id: {}. Cannot create job.", authenticatedUser.getId(), company.getId());
            throw new IllegalArgumentException(JobMessages.USER_IS_NOT_A_CONTACT_OF_COMPANY);
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
        job.setInsuranceRequired(companiesCompanyIdJobsPostRequestDto.isInsuranceRequired());
        job.setAddress(address);
        job.setType(type);
        job.setStartDateTime(companiesCompanyIdJobsPostRequestDto.getStartDateTime());
        job.setEndDateTime(companiesCompanyIdJobsPostRequestDto.getEndDateTime());
        job.setSalaryType(salaryType);
        job.setSalary(companiesCompanyIdJobsPostRequestDto.getSalary());
        job.setMinAge(companiesCompanyIdJobsPostRequestDto.getMinAge());
        job.setMaxAge(companiesCompanyIdJobsPostRequestDto.getMaxAge());
        job.setExperienceRequired(companiesCompanyIdJobsPostRequestDto.isExperienceRequired());
        job.setDressCode(companiesCompanyIdJobsPostRequestDto.getDressCode());
        job.setStatus(JobStatus.OPEN);


        Job savedJob = jobDao.save(job);
        logger.info("Saved corporate job with id: {}", savedJob.getId());


        return jobMapper.toCompaniesCompanyIdJobsPostResponseDto(savedJob);

    }

    @Override
    public Job getById(String jobId) {
        logger.debug("Getting job with id: {}", jobId);

        return jobDao.findById(UUID.fromString(jobId)).orElseThrow(() -> {
            logger.error("Job with id: {} not found.", jobId);
            return new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND);
        });
    }

    @Override
    public boolean existsById(String jobId) {
        logger.info("Checking existence of job with id: {}", jobId);
        return jobDao.existsById(UUID.fromString(jobId));
    }

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "salary", "startDateTime", "title");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Override
    @Transactional(readOnly = true)
    public Job getDetailedById(UUID jobId) {
        return jobDao.findWithDetailsById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<JobSummaryDto> search(JobSearchCriteria criteria, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE_FIELDS);
        Pageable pageable = pageableSanitizer.sanitize(requested, SORTABLE_FIELDS, DEFAULT_SORT);

        Page<UUID> ids = jobDao.searchIds(
                JobStatus.OPEN,
                blankToNull(criteria.getQuery()),
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
                pageable);

        return PageDto.of(ids, jobMapper.toJobSummaryDtoList(inIdOrder(ids.getContent())));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<JobSummaryDto> getJobsOfCurrentUser(Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE_FIELDS);
        Pageable pageable = pageableSanitizer.sanitize(requested, SORTABLE_FIELDS, DEFAULT_SORT);

        Page<Job> jobs = jobDao.findAllByCreatorId(securityService.getAuthenticatedUserId(), pageable);
        return PageDto.of(jobs, jobMapper::toJobSummaryDto);
    }

    @Override
    @Transactional
    public void closeJob(UUID jobId) {
        Job job = jobDao.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND));

        UUID currentUserId = securityService.getAuthenticatedUserId();
        boolean isOwner = job.getCreator() != null && job.getCreator().getId().equals(currentUserId);
        boolean isCompanyContact = job.getCompany() != null
                && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), currentUserId);

        if (!isOwner && !isCompanyContact) {
            throw new ResourceNotFoundException(JobMessages.JOB_NOT_FOUND);
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new IllegalStateException(JobMessages.JOB_NOT_OPEN);
        }

        job.setStatus(JobStatus.CLOSED);
        jobDao.save(job);
        logger.info("Job {} closed by user {}", jobId, currentUserId);
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
}
