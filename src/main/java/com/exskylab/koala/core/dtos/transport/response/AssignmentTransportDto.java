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
public class AssignmentTransportDto {

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

    @Schema(description = "İlanın servisleri, duraklarıyla. İşçi görünümünde plaka ve şoför bilgisi yalnız "
            + "koltuk seçilen serviste dolu; işveren görünümünde hepsinde.")
    private List<ShuttleDto> shuttles;

    @Schema(description = "Bu atamanın seçtiği koltuklar (yön başına en fazla bir).")
    private List<SeatAssignmentDto> seats;
}
