package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.TransportMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobTransportDto {

    @Schema(description = "İşe gidiş yöntemi.")
    private TransportMethod outboundMethod;

    @Schema(description = "İşten dönüş yöntemi.")
    private TransportMethod returnMethod;

    @Schema(types = {"number", "null"}, description = "Yol parası tutarı (TL); yalnız CASH_ALLOWANCE'ta dolu.")
    private BigDecimal allowanceAmount;

    @Schema(description = "Servis sayısı (iki yön toplamı).")
    private int shuttleCount;

    @Schema(description = "Servisler; herkese açık görünüm, durakları ilçe/semt ve saat düzeyinde.")
    private List<PublicShuttleDto> shuttles;
}
