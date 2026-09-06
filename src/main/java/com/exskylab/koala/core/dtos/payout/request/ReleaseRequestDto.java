package com.exskylab.koala.core.dtos.payout.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReleaseRequestDto {

    @Size(max = 512)
    private String reason;
}
