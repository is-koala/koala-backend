package com.exskylab.koala.core.dtos.legal.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConsentDecisionDto {

    @NotNull(message = "{legal.agreement.version.required}")
    private UUID agreementVersionId;

    @NotNull(message = "{legal.granted.required}")
    private Boolean granted;
}
