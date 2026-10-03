package com.exskylab.koala.core.dtos.transport.request;

import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.entities.BoardingKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SeatBoardingDto {

    @Size(max = 256, message = TransportMessages.TOKEN_MAX_LENGTH)
    @Schema(description = "İşçi QR'ındaki {şema}://board?t=... bağlantısının t parametresi (w1. ile başlar). "
            + "Verilirse kaynak EMPLOYER_SCANNED_WORKER_QR olur ve kind yalnız BOARDED olabilir; verilmezse "
            + "listeden işaretleme (EMPLOYER_MARKED).")
    private String token;

    @Schema(description = "Olayın cihazda yakalandığı UTC anı; token verildiğinde zorunlu. Boşsa sunucu saati kullanılır.",
            example = "2026-09-15T03:41:00Z")
    private Instant capturedAt;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Schema(description = "İşaretleme anındaki konum; longitude ile birlikte verilir. Durağa uzaklık FAR_FROM_STOP bayrağı üretir.")
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Schema(description = "İşaretleme anındaki konum; latitude ile birlikte verilir.")
    private BigDecimal longitude;

    @Schema(description = "Çevrimdışı güvenli yeniden deneme kimliği; koltuk başına tekildir. Aynı kimlik aynı gövdeyle "
            + "tekrar gelirse kayıtlı olay döner, farklı gövdeyle gelirse 409 TRANSPORT_BOARDING_IDEMPOTENCY_CONFLICT.",
            example = "3d513c3e-e449-4e0a-ae0e-ef3cdef809e4")
    private UUID clientEventId;

    @Schema(description = "BOARDED (varsayılan) ya da NO_SHOW.")
    private BoardingKind kind;

    @AssertTrue(message = TransportMessages.CAPTURED_AT_NOT_NULL)
    public boolean isCapturedAtPresentWhenTokenPresent() {
        return token == null || token.isBlank() || capturedAt != null;
    }

    @AssertTrue(message = TransportMessages.BOARDING_KIND_RULE)
    public boolean isKindConsistentWithToken() {
        return token == null || token.isBlank() || kind == null || kind == BoardingKind.BOARDED;
    }

    @AssertTrue(message = TransportMessages.BOARDING_POSITION_PAIR)
    public boolean isPositionComplete() {
        return (latitude == null) == (longitude == null);
    }
}
