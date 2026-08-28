package com.exskylab.koala.core.dtos.job.request;


import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.dtos.address.request.CreateAddressRequestDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.UUID;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateIndividualJobDto {

    @UUID(message = JobMessages.CATEGORY_UUID_ID_INVALID)
    private String categoryId;

    @NotBlank(message = JobMessages.TITLE_NOT_BLANK)
    private String title;

    @NotBlank(message = JobMessages.DESCRIPTION_NOT_BLANK)
    private String description;

    private String sector;

    @NotBlank(message = JobMessages.POSITION_NOT_BLANK)
    private String position;

    private String duties;

    @NotNull(message = JobMessages.ADDRESS_NOT_NULL)
    private CreateAddressRequestDto address;

    @NotBlank(message = JobMessages.TYPE_NOT_BLANK)
    private String type;

    @NotBlank(message = JobMessages.SALARY_TYPE_NOT_BLANK)
    private String salaryType;

    @NotNull(message = JobMessages.MINIMUM_SALARY_NOT_NULL)
    @Min(value = 1, message = JobMessages.SALARY_MIN_VALUE)
    private BigDecimal salary;

    @NotNull(message = JobMessages.START_DATE_TIME_NOT_NULL)
    private LocalDateTime startDateTime;

    @NotNull(message = JobMessages.END_DATE_TIME_NOT_NULL)
    private LocalDateTime endDateTime;

    @NotNull(message = JobMessages.MINIMUM_AGE_NOT_NULL)
    @Min(value = 1, message = JobMessages.MINIMUM_AGE_VALUE)
    private Integer minAge;

    @NotNull(message = JobMessages.MAXIMUM_AGE_NOT_NULL)
    @Min(value = 1, message = JobMessages.MAXIMUM_AGE_VALUE)
    private Integer maxAge;

    @NotNull(message = JobMessages.EXPERIENCE_REQUIRED_NOT_NULL)
    private Boolean experienceRequired;

    private String dressCode;

    @Min(value = 1, message = JobMessages.HEADCOUNT_MIN_VALUE)
    private Integer headcount;

    @AssertTrue(message = JobMessages.MAXIMUM_AGE_BELOW_MINIMUM)
    public boolean isAgeRangeOrdered() {
        return minAge == null || maxAge == null || maxAge >= minAge;
    }
}
