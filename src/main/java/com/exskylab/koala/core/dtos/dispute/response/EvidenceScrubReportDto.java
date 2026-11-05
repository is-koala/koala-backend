package com.exskylab.koala.core.dtos.dispute.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record EvidenceScrubReportDto(
        @Schema(description = "Bu çağrıda metadata'sı temizlenip yeni nesneye taşınan kanıt sayısı")
        int scrubbed,
        @Schema(description = "Bu çağrıda temizlenemeyen kanıtlar; satırları ve nesneleri değiştirilmedi")
        List<EvidenceScrubFailureDto> failed,
        @Schema(description = "Hâlâ temizlenmeyi bekleyen ve daha önce başarısız işaretlenmemiş görsel kanıt sayısı; "
                + "sıfır olana kadar uç yeniden çağrılır")
        long remaining) {
}
