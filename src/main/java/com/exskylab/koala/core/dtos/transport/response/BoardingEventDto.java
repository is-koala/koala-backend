package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.BoardingKind;
import com.exskylab.koala.entities.BoardingSource;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

    @Schema(description = "Olayın kaydedildiği andaki durak.")
    private UUID stopId;

    private BoardingKind kind;

    private BoardingSource source;

    @Schema(description = "Biniş anı: capturedAt; cihaz saati makul değilse ya da token'ın kabul aralığının (mint "
            + "penceresinin başından expiresAt'e) dışındaysa sunucu saatine/aralık sınırına çekilir ve "
            + "DEVICE_TIME_IMPLAUSIBLE bayrağı düşer. İlan saatine kırpılmaz.")
    private Instant occurredAt;

    @Schema(types = {"string", "null"}, format = "date-time",
            description = "İstemcinin bildirdiği yakalama anı; listeden işaretlemede boş olabilir.")
    private Instant capturedAt;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @ArraySchema(schema = @Schema(implementation = String.class,
            allowableValues = {"DEVICE_TIME_IMPLAUSIBLE", "FAR_FROM_STOP", "OFF_SCHEDULE", "STATIC_QR", "LATE_SYNC"}),
            arraySchema = @Schema(description = "Makullük bayrakları: FAR_FROM_STOP (gidişte durağa uzak), OFF_SCHEDULE "
                    + "(gidişte durak saatinden sapma), STATIC_QR (sabit QR), LATE_SYNC (süresi geçmiş ama okutulduğu an "
                    + "geçerli token'la geç gönderim), DEVICE_TIME_IMPLAUSIBLE (cihaz saati). Kapı değildir; yalnız kanıt."))
    private Set<TimesheetPlausibilityFlag> flags;

    private UUID recordedById;

    @Schema(description = "Yalnız biniş POST yanıtında anlamlı: true ise yeni olay yazılmadı, kayıtlı olay döndü "
            + "(aynı clientEventId ya da o kalkış için zaten kayıtlı biniş). Listede ve zaman çizelgesinde false.")
    private boolean replayed;
}
