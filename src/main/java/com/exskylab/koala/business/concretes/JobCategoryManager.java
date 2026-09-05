package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.JobCategoryService;
import com.exskylab.koala.core.constants.JobCategoryMessages;
import com.exskylab.koala.core.dtos.jobCategory.request.SaveJobCategoryDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.JobAlertDao;
import com.exskylab.koala.dataAccess.JobCategoryDao;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.dataAccess.JobTemplateDao;
import com.exskylab.koala.entities.JobCategory;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.constants.JobAlertMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class JobCategoryManager implements JobCategoryService {

    private final JobCategoryDao jobCategoryDao;
    private final JobDao jobDao;
    private final JobAlertDao jobAlertDao;
    private final JobTemplateDao jobTemplateDao;

    public JobCategoryManager(JobCategoryDao jobCategoryDao, JobDao jobDao,
                              JobAlertDao jobAlertDao, JobTemplateDao jobTemplateDao) {
        this.jobCategoryDao = jobCategoryDao;
        this.jobDao = jobDao;
        this.jobAlertDao = jobAlertDao;
        this.jobTemplateDao = jobTemplateDao;
    }


    @Override
    public JobCategory getJobCategoryById(UUID categoryId) {
        log.debug("Getting job category with id: {}", categoryId);
        return jobCategoryDao.findById(categoryId).orElseThrow(() -> {
            log.error("Job category with id: {} not found", categoryId);
            return new ResourceNotFoundException(JobCategoryMessages.JOB_CATEGORY_NOT_FOUND_BY_ID);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobCategory> getAllCategories() {
        return jobCategoryDao.findAll(Sort.by("name"));
    }

    @Override
    @Transactional(readOnly = true)
    public JobCategory getById(UUID categoryId) {
        return jobCategoryDao.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAlertMessages.CATEGORY_NOT_FOUND));
    }

    @Override
    @Transactional
    public JobCategory createCategory(SaveJobCategoryDto request) {
        if (jobCategoryDao.existsByCode(request.getCode())) {
            throw new ConflictException(JobCategoryMessages.CODE_ALREADY_EXISTS);
        }

        JobCategory category = new JobCategory();
        category.setCode(request.getCode());
        apply(category, request);

        JobCategory saved = jobCategoryDao.save(category);
        log.info("Job category {} created with id {}", saved.getCode(), saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public JobCategory updateCategory(UUID categoryId, SaveJobCategoryDto request) {
        JobCategory category = requireCategory(categoryId);

        if (jobCategoryDao.existsByCodeAndIdNot(request.getCode(), categoryId)) {
            throw new ConflictException(JobCategoryMessages.CODE_ALREADY_EXISTS);
        }

        category.setCode(request.getCode());
        apply(category, request);

        JobCategory saved = jobCategoryDao.save(category);
        log.info("Job category {} updated", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteCategory(UUID categoryId) {
        JobCategory category = requireCategory(categoryId);

        if (jobDao.existsAnyByCategoryId(categoryId)
                || jobAlertDao.existsAnyByCategoryId(categoryId)
                || jobTemplateDao.existsAnyByCategoryId(categoryId)) {
            throw new ConflictException(JobCategoryMessages.IN_USE);
        }

        jobCategoryDao.delete(category);
        log.info("Job category {} deleted", categoryId);
    }

    private JobCategory requireCategory(UUID categoryId) {
        return jobCategoryDao.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(JobCategoryMessages.JOB_CATEGORY_NOT_FOUND_BY_ID));
    }

    private void apply(JobCategory category, SaveJobCategoryDto request) {
        category.setName(request.getName());
        category.setRequiresInsurance(request.getRequiresInsurance());
    }
}
