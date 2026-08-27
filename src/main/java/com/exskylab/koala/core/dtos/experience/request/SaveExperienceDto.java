package com.exskylab.koala.core.dtos.experience.request;

import com.exskylab.koala.core.constants.ExperienceMessages;
import com.exskylab.koala.entities.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveExperienceDto {

    @NotBlank(message = ExperienceMessages.TITLE_REQUIRED)
    @Size(max = 255)
    private String title;

    @Size(max = 255)
    private String companyName;

    private EmploymentType employmentType;

    @Size(max = 255)
    private String cityName;

    @NotNull(message = ExperienceMessages.START_DATE_REQUIRED)
    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 2000)
    private String description;
}
