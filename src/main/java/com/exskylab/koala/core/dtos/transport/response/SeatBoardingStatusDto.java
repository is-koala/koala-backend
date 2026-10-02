package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.BoardingKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record SeatBoardingStatusDto(
        @Schema(description = "BOARDED ya da işverenin NO_SHOW işareti.")
        BoardingKind kind,
        @Schema(description = "Olayın anı (occurredAt).")
        Instant occurredAt) {
}
