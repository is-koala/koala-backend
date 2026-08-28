package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.JobCategoryService;
import com.exskylab.koala.core.constants.JobCategoryMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.JobCategoryDao;
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

    private JobCategoryDao jobCategoryDao;


    public JobCategoryManager(JobCategoryDao jobCategoryDao) {
        this.jobCategoryDao = jobCategoryDao;
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
}
