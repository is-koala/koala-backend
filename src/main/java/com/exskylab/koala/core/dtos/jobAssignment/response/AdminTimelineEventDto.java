package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.entities.AdminTimelineEventType;
import com.exskylab.koala.entities.BoardingKind;
import com.exskylab.koala.entities.BoardingRecorderRole;
import com.exskylab.koala.entities.BoardingSource;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminTimelineEventDto(
        @Schema(description = "SEAT (koltuk seçimi) ya da BOARDING (biniş/gelmedi kaydı).")
        AdminTimelineEventType type,
        @Schema(description = "Olayın anı; koltukta seçim anı, binişte occurredAt.")
        Instant at,
        UUID id,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING: BOARDED ya da NO_SHOW.")
        BoardingKind kind,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING: kaydın kaynağı.")
        BoardingSource source,
        @ArraySchema(schema = @Schema(implementation = String.class,
                allowableValues = {"DEVICE_TIME_IMPLAUSIBLE", "FAR_FROM_STOP", "OFF_SCHEDULE", "STATIC_QR", "LATE_SYNC"}),
                arraySchema = @Schema(description = "Yalnız BOARDING: makullük bayrakları."))
        Set<TimesheetPlausibilityFlag> flags,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING: kaydı yazan kullanıcı.")
        UUID recordedById,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING: kaydı yazanın rolü (işçi ya da işveren tarafı).")
        BoardingRecorderRole recorderRole,
        UUID shuttleId,
        UUID seatAssignmentId,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING: olay anındaki durak.")
        UUID stopId,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING: olay anındaki durak etiketi.")
        String stopLabel,
        @Schema(types = {"string", "null"}, format = "date-time",
                description = "Yalnız BOARDING: olay anında durağın kalkış saati.")
        Instant stopScheduledAt,
        @Schema(types = {"string", "null"}, format = "date-time",
                description = "Yalnız BOARDING: istemcinin bildirdiği yakalama anı.")
        Instant capturedAt,
        @Schema(types = {"string", "null"}, format = "date-time",
                description = "Yalnız BOARDING: sunucunun kaydı aldığı an.")
        Instant receivedAt,
        @Schema(types = {"number", "null"}, description = "Yalnız BOARDING: okutma/işaretleme konumu; hesap silinince null.")
        BigDecimal latitude,
        @Schema(types = {"number", "null"}, description = "Yalnız BOARDING: okutma/işaretleme konumu; hesap silinince null.")
        BigDecimal longitude,
        @Schema(types = {"integer", "null"}, description = "Yalnız BOARDING: konumun durak pinine metre cinsinden uzaklığı.")
        Integer distanceToStopMeters) {
}
