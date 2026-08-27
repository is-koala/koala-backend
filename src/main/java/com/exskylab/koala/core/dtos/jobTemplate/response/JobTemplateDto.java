package com.exskylab.koala.core.dtos.jobTemplate.response;

import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JobTemplateDto(UUID id, String templateName, UUID companyId, JobCategoryDto category,
                             String title, String description, String sector, String position,
                             String duties, String dressCode, JobType type, SalaryType salaryType,
                             BigDecimal salary, Integer minAge, Integer maxAge,
                             Boolean experienceRequired, Boolean insuranceRequired, Instant createdAt) {
}
