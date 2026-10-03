package com.exskylab.koala.core.dtos.transport.request;

import com.exskylab.koala.core.constants.TransportMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ShuttleBoardingDto {

    @NotBlank(message = TransportMessages.TOKEN_NOT_BLANK)
    @Size(max = 256, message = TransportMessages.TOKEN_MAX_LENGTH)
    @Schema(description = "Servis QR'ındaki {şema}://board?t=... bağlantısının t parametresi (s1. ile başlar).")
    private String token;

    @NotNull(message = TransportMessages.CAPTURED_AT_NOT_NULL)
    @Schema(description = "QR'ın cihazda okunduğu UTC anı. Biniş anı olarak saklanır; sunucu saatinden "
            + "makul olmayan ölçüde saparsa DEVICE_TIME_IMPLAUSIBLE bayrağıyla sunucu saati kullanılır.",
            example = "2026-09-15T03:41:00Z")
    private Instant capturedAt;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Schema(description = "Okutma anındaki konum; longitude ile birlikte verilir. Durağa uzaklık FAR_FROM_STOP bayrağı üretir.")
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Schema(description = "Okutma anındaki konum; latitude ile birlikte verilir.")
    private BigDecimal longitude;

    @Schema(description = "Çevrimdışı güvenli yeniden deneme kimliği; koltuk başına tekildir. Aynı kimlik aynı gövdeyle "
            + "tekrar gelirse kayıtlı olay döner, farklı gövdeyle gelirse 409 TRANSPORT_BOARDING_IDEMPOTENCY_CONFLICT.",
            example = "3d513c3e-e449-4e0a-ae0e-ef3cdef809e4")
    private UUID clientEventId;

    @AssertTrue(message = TransportMessages.BOARDING_POSITION_PAIR)
    public boolean isPositionComplete() {
        return (latitude == null) == (longitude == null);
    }
}
