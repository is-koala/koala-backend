package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.entities.BoardingKind;
import com.exskylab.koala.entities.BoardingSource;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminTimelineEventDto(
        @Schema(description = "Olay türü: SEAT (koltuk seçimi) ya da BOARDING (biniş/gelmedi kaydı).")
        String type,
        @Schema(description = "Olayın anı; koltukta seçim anı, binişte occurredAt.")
        Instant at,
        UUID id,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING satırında: BOARDED ya da NO_SHOW.")
        BoardingKind kind,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING satırında: kaydın kaynağı.")
        BoardingSource source,
        @Schema(description = "Yalnız BOARDING satırında: makullük bayrakları.")
        Set<TimesheetPlausibilityFlag> flags,
        @Schema(types = {"string", "null"}, description = "Yalnız BOARDING satırında: kaydı yazan kullanıcı.")
        UUID recordedById,
        UUID shuttleId,
        UUID seatAssignmentId) {
}
