package com.exskylab.koala.core.dtos.job.response;

import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;
import com.exskylab.koala.entities.AddressPrecision;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobSummaryDto {

    private UUID id;
    private String title;
    private String position;
    private String sector;
    private JobCategoryDto category;
    private JobEmployerType employerType;
    private JobType type;
    private SalaryType salaryType;
    private BigDecimal salary;
    private PricingBreakdownDto pricing;
    private boolean insuranceRequired;
    private boolean experienceRequired;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private JobStatus status;

    @Schema(types = {"string", "null"}, format = "date-time",
            description = "İlanın veri tabanında son değiştiği an; sitemap ve önbellek tazeliğinin kaynağıdır. Eski kayıtlarda null olabilir.",
            example = "2026-09-09T10:11:12Z")
    private Instant updatedAt;

    private String cityName;
    private String districtName;
    private String companyName;

    private String coverImageUrl;

    private long applicationCount;

    private Integer headcount;

    private long hiredCount;

    private Double latitude;

    private Double longitude;

    @Schema(description = "Harita koordinatının yaklaşık mı kesin mi olduğunu belirtir.")
    private AddressPrecision addressPrecision;
}
