package com.exskylab.koala.core.dtos.job.response;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.company.response.OnlyCompanyDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobDetailDto {

    private UUID id;
    private String title;
    private String description;
    private String duties;
    private String sector;
    private String position;
    private String dressCode;
    private JobCategoryDto category;
    private JobEmployerType employerType;
    private JobType type;
    private SalaryType salaryType;
    private BigDecimal salary;
    private PricingBreakdownDto pricing;
    private int minAge;
    private int maxAge;
    private boolean insuranceRequired;
    private boolean experienceRequired;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private JobStatus status;
    private AddressDto address;
    private SafeUserDto creator;
    private OnlyCompanyDto company;
    private List<GalleryImageDto> images;
    private Instant createdAt;
    private Integer headcount;
    private long hiredCount;
}
