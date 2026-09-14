package com.exskylab.koala.core.dtos.job.response;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.company.response.JobCompanyDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.core.dtos.user.response.StaffPartyDto;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AdminJobDetailDto(UUID id, String title, String description, String duties, String sector,
                                String position, String dressCode, JobCategoryDto category, JobStatus status,
                                JobEmployerType employerType, JobType type, SalaryType salaryType,
                                BigDecimal salary, PricingBreakdownDto pricing, Integer minAge, Integer maxAge,
                                Boolean insuranceRequired, Boolean experienceRequired, Integer headcount,
                                LocalDateTime startDateTime, LocalDateTime endDateTime,
                                StaffPartyDto employer, JobCompanyDto company,
                                AddressDto address, Boolean addressPublic, List<GalleryImageDto> images,
                                Long applicationCount, Long assignmentCount,
                                Instant createdAt, Instant updatedAt) {
}
