package com.exskylab.koala.core.dtos.dispute.request;

import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.entities.DisputeResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResolveDisputeDto {

    @NotNull(message = DisputeMessages.RESOLUTION_REQUIRED)
    private DisputeResolution resolution;

    @Size(max = 2000)
    private String note;
}
