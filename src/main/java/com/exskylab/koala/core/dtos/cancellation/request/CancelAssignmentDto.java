package com.exskylab.koala.core.dtos.cancellation.request;

import com.exskylab.koala.core.constants.CancellationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CancelAssignmentDto {

    @NotBlank(message = CancellationMessages.REASON_REQUIRED)
    @Size(max = 1000)
    private String reason;
}
