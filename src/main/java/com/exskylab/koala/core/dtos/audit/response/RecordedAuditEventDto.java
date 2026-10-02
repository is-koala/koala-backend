package com.exskylab.koala.core.dtos.audit.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record RecordedAuditEventDto(
        @Schema(description = "Yazılan denetim satırının kimliği; GET /admin/audit-log satırındaki id ile aynı.")
        UUID id,
        @Schema(description = "Kayda geçen eylem kodu (EXPORT için \"export\").")
        String action,
        @Schema(description = "Dışa aktarılan listenin denetim kaynak türü (ör. \"job-assignments\").")
        String resourceType,
        @Schema(description = "Kaydın zamanı.")
        Instant occurredAt) {
}
