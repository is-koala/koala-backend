package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.TransportDirection;
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
public class SeatAssignmentDto {

    private UUID id;

    @Schema(description = "Koltuğun yönü; atama başına yön başına tek koltuk vardır.")
    private TransportDirection direction;

    private UUID shuttleId;

    private UUID stopId;

    @Schema(types = {"string", "null"}, format = "date-time",
            description = "Koltuğun seçildiği an. Personel zaman çizelgesi bu alanı kullanır.")
    private Instant createdAt;

    @Schema(description = "Bu koltuğun güncel servisi ve kalkış günü için biniş durumu: BOARDED olayı, yoksa son "
            + "NO_SHOW işareti; kayıt yoksa null.")
    private SeatBoardingStatusDto boarding;
}
