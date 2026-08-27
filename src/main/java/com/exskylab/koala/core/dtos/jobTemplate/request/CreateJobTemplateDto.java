package com.exskylab.koala.core.dtos.jobTemplate.request;

import com.exskylab.koala.core.constants.JobTemplateMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateJobTemplateDto {

    @NotBlank(message = JobTemplateMessages.NAME_REQUIRED)
    @Size(max = 255)
    private String templateName;

    @NotNull(message = JobTemplateMessages.JOB_NOT_FOUND)
    private UUID jobId;
}
