package com.exskylab.koala.core.dtos.company.request;

import com.exskylab.koala.core.constants.AdminMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectCompanyDto {

    @NotBlank(message = AdminMessages.REJECTION_REASON_NOT_BLANK)
    @Size(max = 1000, message = AdminMessages.DECISION_NOTE_TOO_LONG)
    private String reason;

}
