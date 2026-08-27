package com.exskylab.koala.core.dtos.dispute.request;

import com.exskylab.koala.core.constants.DisputeMessages;
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
public class OpenDisputeDto {

    @NotBlank(message = DisputeMessages.REASON_REQUIRED)
    @Size(max = 2000)
    private String reason;
}
