package com.exskylab.koala.core.dtos.transport.request;

import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.entities.TransportDirection;
import com.exskylab.koala.entities.TransportMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class TransportPlanWriteDto {

    @NotNull(message = TransportMessages.OUTBOUND_METHOD_NOT_NULL)
    @Schema(description = "İşe gidiş yöntemi.")
    private TransportMethod outboundMethod;

    @NotNull(message = TransportMessages.RETURN_METHOD_NOT_NULL)
    @Schema(description = "İşten dönüş yöntemi.")
    private TransportMethod returnMethod;

    @PositiveOrZero(message = TransportMessages.ALLOWANCE_AMOUNT_RULE)
    @Digits(integer = 17, fraction = 2, message = TransportMessages.ALLOWANCE_AMOUNT_RULE)
    @Schema(types = {"number", "null"},
            description = "Yol parası tutarı (TL). Yöntemlerden biri CASH_ALLOWANCE ise zorunlu, değilse boş. "
                    + "Bilgi amaçlıdır; para yolunun dışındadır, işveren elden verir.",
            example = "150.00")
    private BigDecimal allowanceAmount;

    @Size(max = 500, message = TransportMessages.ALLOWANCE_NOTE_MAX_LENGTH)
    @Schema(types = {"string", "null"}, description = "Yol parasıyla ilgili not (ör. ne zaman verileceği).")
    private String allowanceNote;

    @Size(max = 1000, message = TransportMessages.NOTES_MAX_LENGTH)
    @Schema(types = {"string", "null"}, description = "Ulaşımla ilgili genel not; atanmış işçiler görür.")
    private String notes;

    @Valid
    @Schema(description = "Servisler, verildiği sırayla. Boş liste servisleri kaldırır.")
    private List<ShuttleWriteDto> shuttles;

    @JsonIgnore
    @AssertTrue(message = TransportMessages.ALLOWANCE_AMOUNT_RULE)
    public boolean isAllowanceAmountConsistent() {
        boolean allowance = outboundMethod == TransportMethod.CASH_ALLOWANCE
                || returnMethod == TransportMethod.CASH_ALLOWANCE;
        return allowance ? allowanceAmount != null : allowanceAmount == null;
    }

    @JsonIgnore
    @AssertTrue(message = TransportMessages.SHUTTLE_DIRECTION_RULE)
    public boolean isEveryShuttleOnAShuttleDirection() {
        if (shuttles == null) {
            return true;
        }
        return shuttles.stream().allMatch(shuttle -> shuttle == null || shuttle.getDirection() == null
                || methodOf(shuttle.getDirection()) == TransportMethod.SHUTTLE);
    }

    private TransportMethod methodOf(TransportDirection direction) {
        return direction == TransportDirection.OUTBOUND ? outboundMethod : returnMethod;
    }
}
