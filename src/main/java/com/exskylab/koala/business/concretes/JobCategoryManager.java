package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobCategoryService;
import com.exskylab.koala.core.constants.JobCategoryMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.JobCategoryDao;
import com.exskylab.koala.entities.JobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class JobCategoryManager implements JobCategoryService {

    private final Logger logger = LoggerFactory.getLogger(JobCategoryManager.class);

    private JobCategoryDao jobCategoryDao;


    public JobCategoryManager(JobCategoryDao jobCategoryDao) {
        this.jobCategoryDao = jobCategoryDao;
    }


    @Override
    public JobCategory getJobCategoryById(UUID categoryId) {
        logger.debug("Getting job category with id: {}", categoryId);
        return jobCategoryDao.findById(categoryId).orElseThrow(() -> {
            logger.error("Job category with id: {} not found", categoryId);
            return new ResourceNotFoundException(JobCategoryMessages.JOB_CATEGORY_NOT_FOUND_BY_ID);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobCategory> getAllCategories() {
        return jobCategoryDao.findAll(Sort.by("name"));
    }
}
