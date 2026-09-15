package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.TransportMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransportPlanDto {

    private UUID id;

    private UUID jobId;

    @Schema(description = "İşe gidiş yöntemi.")
    private TransportMethod outboundMethod;

    @Schema(description = "İşten dönüş yöntemi.")
    private TransportMethod returnMethod;

    @Schema(types = {"number", "null"}, description = "Yol parası tutarı (TL); yalnız CASH_ALLOWANCE'ta dolu.")
    private BigDecimal allowanceAmount;

    @Schema(types = {"string", "null"})
    private String allowanceNote;

    @Schema(types = {"string", "null"})
    private String notes;

    @Schema(description = "Servisler, sırayla; işveren görünümünde plaka ve şoför bilgisi dahil.")
    private List<ShuttleDto> shuttles;

    @Schema(types = {"string", "null"}, format = "date-time", description = "Planın son değiştiği an.")
    private Instant updatedAt;
}
