package com.exskylab.koala.core.dtos.job.response;

import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.core.dtos.user.response.StaffPartyDto;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminJobDto(UUID id, String title, JobStatus status, JobEmployerType employerType, JobType type,
                          SalaryType salaryType, BigDecimal salary, PricingBreakdownDto pricing,
                          LocalDateTime startDateTime, LocalDateTime endDateTime,
                          StaffPartyDto employer, UUID companyId, String companyName,
                          String cityName, String districtName, Integer headcount,
                          Long applicationCount, Long assignmentCount,
                          Instant createdAt, Instant updatedAt) {
}
