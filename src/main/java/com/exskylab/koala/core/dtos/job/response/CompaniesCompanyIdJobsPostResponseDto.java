package com.exskylab.koala.core.dtos.job.response;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.company.response.OnlyCompanyDto;
import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompaniesCompanyIdJobsPostResponseDto {

    private UUID id;

    private OnlyCompanyDto company;

    private SafeUserDto creator;

    private JobCategoryDto category;

    private JobEmployerType employerType;

    private String title;

    private String description;

    private String sector;

    private String position;

    private String duties;

    private boolean insuranceRequired;

    private AddressDto address;

    private JobType type;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    private SalaryType salaryType;

    private BigDecimal salary;

    private int minAge;

    private int maxAge;

    private boolean experienceRequired;

    private String dressCode;

    private JobStatus status;

}
