package com.exskylab.koala.core.dtos.jobAssignment.request;

import com.exskylab.koala.core.constants.AuthMessages;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobAssignmentsAssignmentIdStatusPatchRequestDto {
    @NotNull(message = AuthMessages.ANSWER_NOT_NULL)
    private Boolean accepted;
}
