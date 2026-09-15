package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.TransportDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShuttleDto {

    private UUID id;

    private TransportDirection direction;

    private String name;

    @Schema(types = {"string", "null"},
            description = "Plaka. İşveren her serviste görür; işçi yalnız koltuk seçtiği serviste görür, diğerlerinde null.")
    private String plate;

    private int capacity;

    @Schema(description = "Serviste seçilmiş koltuk sayısı; capacity ile karşılaştırılarak doluluk okunur.")
    private long seatsTaken;

    @Schema(types = {"string", "null"},
            description = "Şoför adı. İşveren her serviste görür; işçi yalnız koltuk seçtiği serviste görür.")
    private String driverName;

    @Schema(types = {"string", "null"},
            description = "Şoför telefonu. İşveren her serviste görür; işçi yalnız koltuk seçtiği serviste görür.")
    private String driverPhone;

    private int sortOrder;

    @Schema(description = "Duraklar, sırayla.")
    private List<ShuttleStopDto> stops;
}
