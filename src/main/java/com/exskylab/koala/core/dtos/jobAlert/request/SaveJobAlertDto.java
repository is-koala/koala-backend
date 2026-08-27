package com.exskylab.koala.core.dtos.jobAlert.request;

import com.exskylab.koala.core.constants.JobAlertMessages;
import com.exskylab.koala.entities.JobType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class SaveJobAlertDto {

    @NotBlank(message = JobAlertMessages.NAME_REQUIRED)
    @Size(max = 255)
    private String alertName;

    private UUID categoryId;

    @Size(max = 255)
    private String cityName;

    @Size(max = 255)
    private String districtName;

    @DecimalMin(value = "0.00")
    private BigDecimal minSalary;

    private JobType type;

    private Boolean active;
}
