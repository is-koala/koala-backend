package com.exskylab.koala.core.dtos.transport.request;

import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.entities.TransportDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ShuttleWriteDto {

    @Schema(types = {"string", "null"}, format = "uuid",
            description = "Var olan servisin kimliği. Verilirse servis yerinde güncellenir ve koltuk seçimleri korunur; "
                    + "boşsa yeni servis açılır. Listede yer almayan mevcut servisler koltuklarıyla birlikte kaldırılır.")
    private UUID id;

    @NotNull(message = TransportMessages.SHUTTLE_DIRECTION_NOT_NULL)
    @Schema(description = "Servisin yönü; yalnız yöntemi SHUTTLE olan yönde servis tanımlanabilir.")
    private TransportDirection direction;

    @NotBlank(message = TransportMessages.SHUTTLE_NAME_NOT_BLANK)
    @Size(max = 120, message = TransportMessages.SHUTTLE_NAME_MAX_LENGTH)
    @Schema(description = "Servisin adı; ilan detayında herkese görünür.", example = "Anadolu yakası servisi")
    private String name;

    @Size(max = 20, message = TransportMessages.SHUTTLE_PLATE_MAX_LENGTH)
    @Schema(types = {"string", "null"}, description = "Plaka; yalnız atanmış işçi ve işveren görür.",
            example = "34 ABC 12")
    private String plate;

    @NotNull(message = TransportMessages.SHUTTLE_CAPACITY_NOT_NULL)
    @Min(value = 1, message = TransportMessages.SHUTTLE_CAPACITY_RANGE)
    @Max(value = 100, message = TransportMessages.SHUTTLE_CAPACITY_RANGE)
    @Schema(description = "Koltuk sayısı (1–100). Dolu servise koltuk seçilemez; mevcut koltuk sayısının altına indirilemez.")
    private Integer capacity;

    @Size(max = 120, message = TransportMessages.DRIVER_NAME_MAX_LENGTH)
    @Schema(types = {"string", "null"}, description = "Şoför adı; yalnız atanmış işçi ve işveren görür.")
    private String driverName;

    @Size(max = 32, message = TransportMessages.DRIVER_PHONE_MAX_LENGTH)
    @Schema(types = {"string", "null"}, description = "Şoför telefonu; yalnız atanmış işçi ve işveren görür.")
    private String driverPhone;

    @Valid
    @NotEmpty(message = TransportMessages.STOPS_NOT_EMPTY)
    @Schema(description = "Duraklar, verildiği sırayla. En az bir durak gerekir.")
    private List<ShuttleStopWriteDto> stops;
}
