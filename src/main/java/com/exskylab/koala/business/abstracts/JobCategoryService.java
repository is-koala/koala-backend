package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.JobCategory;

import java.util.List;
import java.util.UUID;

public interface JobCategoryService {
    JobCategory getJobCategoryById(UUID categoryId);

    List<JobCategory> getAllCategories();

    JobCategory getById(UUID categoryId);
}
