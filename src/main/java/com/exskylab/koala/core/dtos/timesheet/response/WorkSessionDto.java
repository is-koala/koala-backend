package com.exskylab.koala.core.dtos.timesheet.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record WorkSessionDto(UUID id, UUID jobAssignmentId, Instant checkInAt, Instant checkOutAt,
                             UUID checkInClientEventId, Instant checkInCapturedAt, Instant checkInReceivedAt,
                             @ArraySchema(schema = @Schema(implementation = String.class,
                                     allowableValues = {"DEVICE_TIME_IMPLAUSIBLE", "OUTSIDE_JOB_WINDOW", "NON_MONOTONIC"}))
                             Set<TimesheetPlausibilityFlag> checkInPlausibilityFlags,
                             UUID checkOutClientEventId, Instant checkOutCapturedAt, Instant checkOutReceivedAt,
                             @ArraySchema(schema = @Schema(implementation = String.class,
                                     allowableValues = {"DEVICE_TIME_IMPLAUSIBLE", "OUTSIDE_JOB_WINDOW", "NON_MONOTONIC"}))
                             Set<TimesheetPlausibilityFlag> checkOutPlausibilityFlags,
                             @Schema(types = {"number", "null"}, description = LOCATION)
                             BigDecimal checkInLatitude,
                             @Schema(types = {"number", "null"}, description = LOCATION)
                             BigDecimal checkInLongitude,
                             @Schema(types = {"number", "null"}, description = LOCATION)
                             BigDecimal checkOutLatitude,
                             @Schema(types = {"number", "null"}, description = LOCATION)
                             BigDecimal checkOutLongitude,
                             @Schema(description = WITHHELD + "Giriş konumu (checkInLatitude/checkInLongitude).")
                             boolean checkInLocationWithheld,
                             @Schema(description = WITHHELD + "Çıkış konumu (checkOutLatitude/checkOutLongitude).")
                             boolean checkOutLocationWithheld,
                             Integer minutes) {

    private static final String LOCATION = "Giriş/çıkış anındaki cihaz konumu (istemci göndermediyse null). Personel "
            + "görünümünde (GET /admin/job-assignments/{id}) personel user.read_pii taşımıyorsa null.";

    private static final String WITHHELD = "Konum gizlendi: konum kayıtlı ama personel görünümünde "
            + "(GET /admin/job-assignments/{id}) personel user.read_pii taşımadığı için null döndü. Konum hiç "
            + "alınmamışsa, gösteriliyorsa ve personel dışı görünümlerde false. ";
}
