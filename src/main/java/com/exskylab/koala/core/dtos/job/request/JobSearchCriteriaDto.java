package com.exskylab.koala.core.dtos.job.request;

import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobSearchCriteriaDto {

    private String query;
    private UUID categoryId;
    private UUID cityId;
    private UUID districtId;
    private JobType type;
    private SalaryType salaryType;
    private JobEmployerType employerType;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private Boolean insuranceRequired;
    private Boolean experienceRequired;

    private Double minLatitude;

    private Double maxLatitude;

    private Double minLongitude;

    private Double maxLongitude;
}
