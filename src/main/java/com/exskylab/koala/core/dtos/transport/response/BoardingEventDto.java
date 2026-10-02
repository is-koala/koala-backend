package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.BoardingKind;
import com.exskylab.koala.entities.BoardingSource;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoardingEventDto {

    private UUID id;

    private UUID seatAssignmentId;

    private UUID jobAssignmentId;

    @Schema(description = "Olayın kaydedildiği andaki servis; koltuk sonradan başka servise taşınsa da değişmez.")
    private UUID shuttleId;

    @Schema(description = "BOARDED ya da NO_SHOW.")
    private BoardingKind kind;

    private BoardingSource source;

    @Schema(description = "Biniş anı: capturedAt, makul değilse sunucu saati. İlan saatine kırpılmaz.")
    private Instant occurredAt;

    @Schema(types = {"string", "null"}, format = "date-time",
            description = "İstemcinin bildirdiği yakalama anı; listeden işaretlemede boş olabilir.")
    private Instant capturedAt;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Schema(description = "Makullük bayrakları: FAR_FROM_STOP (durağa uzak), OFF_SCHEDULE (durak saatinden sapma), "
            + "STATIC_QR (sabit QR), DEVICE_TIME_IMPLAUSIBLE (cihaz saati). Kapı değildir; yalnız kanıt.")
    private Set<TimesheetPlausibilityFlag> flags;

    private UUID recordedById;
}
