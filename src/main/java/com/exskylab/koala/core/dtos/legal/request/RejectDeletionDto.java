package com.exskylab.koala.core.dtos.legal.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectDeletionDto(
        @NotBlank(message = "{legal.deletion.rejection.reason.required}")
        @Size(max = 512)
        String reason) {
}
