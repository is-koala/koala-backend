package com.exskylab.koala.core.dtos.jobAlert.response;

import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.entities.JobType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JobAlertDto(UUID id, String alertName, JobCategoryDto category, String cityName,
                          String districtName, BigDecimal minSalary, JobType type,
                          boolean active, Instant lastNotifiedAt) {
}
