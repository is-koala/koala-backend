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
        @Schema(description = "Satırın konusu olan pazaryeri tarafının türü (USER ya da COMPANY). Yazma satırlarında "
                + "(mutating=true; personel notu ve işareti dahil) ve hassas okumalarda (sensitivity PERSONAL_DATA ya "
                + "da FINANCIAL) dolar; sıradan okuma satırlarında, katalog/ayar/personel uçlarında ve konusu "
                + "bulunamayan kayıtta (var olmayan kimlik) null. Alan eklenmeden önceki yazma satırları "
                + "türetilebildiği ölçüde geri dolduruldu, türetilemeyenler null.")
        AuditSubjectType subjectType,
        @Schema(description = "Kararın konusu: kimlik incelemesinde ve hesap silme talebinde kullanıcı, şirket "
                + "onay/redde şirket, görselde galerinin sahibi, IBAN doğrulamasında hesap sahibi, hakedişte işçi, "
                + "atama/uyuşmazlık işlemlerinde işçi, şikayette şikayet edilen (yoksa şikayet edilen ilanın "
                + "işvereni, o da yoksa şikayet edilen görselin sahibi), not/işarette hedefin kendisi ya da tarafı. "
                + "subjectType ile aynı kurala göre null olabilir.")
        UUID subjectId,
        @Schema(description = "Karşı tarafın türü (USER ya da COMPANY); iki taraflı satırlarda dolar — atama "
                + "işlemleri, uyuşmazlık çözümü, ödeme okuması ve atama/uyuşmazlık üzerine yazılan personel notu ya da "
                + "işareti. Diğer satırlarda null.")
        AuditSubjectType relatedSubjectType,
        @Schema(description = "Karşı taraf: iki taraflı satırlarda işveren — şirket ilanında şirket, bireysel ilanda "
                + "ilanı açan kullanıcı. Diğer satırlarda null.")
        UUID relatedSubjectId) {
}
