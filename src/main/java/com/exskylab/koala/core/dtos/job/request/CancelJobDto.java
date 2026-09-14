package com.exskylab.koala.core.dtos.job.request;

import com.exskylab.koala.core.constants.JobMessages;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelJobDto {

    @Size(max = 500, message = JobMessages.CANCEL_REASON_TOO_LONG)
    private String reason;
}
