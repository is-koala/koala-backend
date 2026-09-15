package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShuttleStopDto {

    private UUID id;

    @Schema(types = {"string", "null"}, description = "Durağın kısa adı.")
    private String label;

    @Schema(description = "Servisin bu duraktan kalkacağı an (UTC).")
    private Instant scheduledAt;

    private int sortOrder;

    @Schema(description = "Durağın tam adresi; yalnız işveren ve atanmış işçi görür.")
    private AddressDto address;

    @Schema(description = "Bu durağı seçen koltuk sayısı.")
    private long seatsTaken;
}
