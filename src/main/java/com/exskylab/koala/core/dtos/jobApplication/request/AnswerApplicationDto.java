package com.exskylab.koala.core.dtos.jobApplication.request;

import com.exskylab.koala.core.constants.JobApplicationMessages;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AnswerApplicationDto {


    @NotBlank(message = JobApplicationMessages.STATUS_NOT_BLANK)
    private String status;

    private String rejectionReason;

}
