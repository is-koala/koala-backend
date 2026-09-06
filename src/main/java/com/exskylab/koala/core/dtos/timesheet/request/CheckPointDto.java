package com.exskylab.koala.core.dtos.timesheet.request;

import com.exskylab.koala.core.customValidations.ValidTimesheetEvent;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ValidTimesheetEvent
public class CheckPointDto {

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal longitude;

    @Schema(description = "Çevrimdışı güvenli yeniden deneme kimliği; capturedAt ile birlikte gönderilir",
            example = "3d513c3e-e449-4e0a-ae0e-ef3cdef809e4")
    private UUID clientEventId;

    @Schema(description = "Olayın cihazda yakalandığı UTC anı; clientEventId ile birlikte gönderilir",
            example = "2026-09-06T10:00:00Z")
    private Instant capturedAt;
}
