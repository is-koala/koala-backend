package com.exskylab.koala.core.dtos.jobCategory.request;

import com.exskylab.koala.core.constants.JobCategoryMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveJobCategoryDto {

    @NotBlank(message = JobCategoryMessages.CODE_REQUIRED)
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = JobCategoryMessages.CODE_FORMAT)
    private String code;

    @NotBlank(message = JobCategoryMessages.NAME_REQUIRED)
    @Size(max = 255)
    private String name;

    @NotNull(message = JobCategoryMessages.INSURANCE_FLAG_REQUIRED)
    private Boolean requiresInsurance;
}
