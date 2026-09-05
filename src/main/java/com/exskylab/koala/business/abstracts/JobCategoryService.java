package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.jobCategory.request.SaveJobCategoryDto;
import com.exskylab.koala.entities.JobCategory;

import java.util.List;
import java.util.UUID;

public interface JobCategoryService {
    JobCategory getJobCategoryById(UUID categoryId);

    List<JobCategory> getAllCategories();

    JobCategory getById(UUID categoryId);

    JobCategory createCategory(SaveJobCategoryDto request);

    JobCategory updateCategory(UUID categoryId, SaveJobCategoryDto request);

    void deleteCategory(UUID categoryId);
}
