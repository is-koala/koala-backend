package com.exskylab.koala.core.dtos.transport.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShuttleBoardingRowDto {

    private UUID seatAssignmentId;

    private UUID jobAssignmentId;

    private UUID workerId;

    @Schema(types = {"string", "null"}, description = "İşçi hesabını silmişse null; satır kalır.")
    private String firstName;

    @Schema(types = {"string", "null"}, description = "İşçi hesabını silmişse null; satır kalır.")
    private String lastName;

    private UUID stopId;

    @Schema(description = "Durağın kalkış gününde BOARDED olayı varsa true.")
    private boolean boarded;

    @Schema(description = "Satırın durumunu belirleyen olay: o günün BOARDED olayı, yoksa son NO_SHOW işareti, "
            + "hiç kayıt yoksa null (henüz binmedi).")
    private BoardingEventDto event;
}
