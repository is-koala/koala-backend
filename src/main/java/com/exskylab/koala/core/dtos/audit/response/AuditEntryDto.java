package com.exskylab.koala.core.dtos.audit.response;

import com.exskylab.koala.entities.AuditSubjectType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryDto(
        UUID id,
        Instant occurredAt,
        UUID actorStaffId,
        String actorDisplayName,
        String permission,
        String action,
        String resourceType,
        String resourceId,
        boolean allowed,
        boolean mutating,
        String sensitivity,
        String requestBody,
        String ip,
        String userAgent,
        String before,
        String after,
        @Schema(description = "Kararın konusu olan pazaryeri tarafının türü (USER ya da COMPANY). Yalnız yazma "
                + "satırlarında (mutating=true) dolar; okuma satırlarında, katalog/ayar/personel uçlarında, not ve "
                + "işaret yazımlarında ve konusu bulunamayan kayıtta null. Alan eklenmeden önceki satırlar "
                + "türetilebildiği ölçüde geri dolduruldu, türetilemeyenler null.")
        AuditSubjectType subjectType,
        @Schema(description = "Kararın konusu: kimlik incelemesinde ve hesap silme talebinde kullanıcı, şirket "
                + "onay/redde şirket, görselde galerinin sahibi, IBAN doğrulamasında hesap sahibi, hakedişte işçi, "
                + "atama/uyuşmazlık işlemlerinde işçi, şikayette şikayet edilen (yoksa şikayet edilen ilanın "
                + "işvereni). subjectType ile aynı kurala göre null olabilir.")
        UUID subjectId,
        @Schema(description = "Karşı tarafın türü (USER ya da COMPANY); yalnız atama ve uyuşmazlık işlemlerinde "
                + "dolar, diğer satırlarda null.")
        AuditSubjectType relatedSubjectType,
        @Schema(description = "Karşı taraf: atama ve uyuşmazlık işlemlerinde işveren — şirket ilanında şirket, "
                + "bireysel ilanda ilanı açan kullanıcı. Diğer satırlarda null.")
        UUID relatedSubjectId) {
}
