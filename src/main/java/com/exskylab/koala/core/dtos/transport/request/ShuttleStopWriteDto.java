package com.exskylab.koala.core.dtos.transport.request;

import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ShuttleStopWriteDto {

    @Schema(types = {"string", "null"}, format = "uuid",
            description = "Var olan durağın kimliği. Verilirse durak yerinde güncellenir ve koltuk seçimleri korunur; "
                    + "boşsa yeni durak açılır.")
    private UUID id;

    @Size(max = 120, message = TransportMessages.STOP_LABEL_MAX_LENGTH)
    @Schema(description = "Durağın kısa adı; bildirimlerde ve listede gösterilir.", example = "Kadıköy İskele")
    private String label;

    @NotNull(message = TransportMessages.STOP_SCHEDULED_AT_NOT_NULL)
    @Schema(description = "Servisin bu duraktan kalkacağı an (UTC).", example = "2026-09-20T03:40:00Z")
    private Instant scheduledAt;

    @Valid
    @Schema(description = "Durak adresi. Yeni durakta zorunlu; var olan durakta verilirse adres değiştirilir, "
            + "boş bırakılırsa mevcut adres korunur.")
    private AddressWriteDto address;

    @JsonIgnore
    @AssertTrue(message = TransportMessages.STOP_ADDRESS_REQUIRED)
    public boolean isAddressPresentForNewStop() {
        return id != null || address != null;
    }
}
