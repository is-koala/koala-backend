package com.exskylab.koala.core.dtos.jobApplication.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Atamayı doğuran başvurunun zaman çizelgesi; doğrudan atamada null.")
public record AssignmentApplicationDto(UUID id,
                                       @Schema(description = "İşçinin başvurduğu an.") Instant applicationDateTime,
                                       @Schema(types = {"string", "null"}, format = "date-time",
                                               description = "İşverenin başvuruyu kabul ettiği an; kabul anı budur.")
                                       Instant answeredDateTime) {
}
