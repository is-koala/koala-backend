package com.exskylab.koala.core.dtos.transport.request;

import com.exskylab.koala.core.constants.TransportMessages;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class SeatSelectionDto {

    @NotNull(message = TransportMessages.SEAT_SHUTTLE_NOT_NULL)
    @Schema(description = "Seçilen servis; yön servisin yönünden türer ve o yöndeki önceki seçimin yerine geçer.")
    private UUID shuttleId;

    @NotNull(message = TransportMessages.SEAT_STOP_NOT_NULL)
    @Schema(description = "Seçilen servisin durağı.")
    private UUID stopId;
}
