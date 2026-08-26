package com.exskylab.koala.core.dtos.jobApplication.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobsJobIdApplicationsPostRequestDto {

    @Size(max = 2000)
    private String notes;

}
