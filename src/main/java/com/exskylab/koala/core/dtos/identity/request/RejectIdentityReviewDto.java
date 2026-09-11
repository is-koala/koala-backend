package com.exskylab.koala.core.dtos.identity.request;

import com.exskylab.koala.core.constants.IdentityMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectIdentityReviewDto {

    @NotBlank(message = IdentityMessages.REJECTION_REASON_REQUIRED)
    @Size(max = 1000)
    private String reason;
}
