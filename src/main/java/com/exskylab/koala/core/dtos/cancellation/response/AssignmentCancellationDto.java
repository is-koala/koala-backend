package com.exskylab.koala.core.dtos.cancellation.response;

import com.exskylab.koala.entities.CancellationParty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Atama iptal edilmişse iptal özeti; iptal yoksa null. Para alanları exact decimal string'dir.")
public record AssignmentCancellationDto(
        @Schema(description = "İptali hangi tarafın yaptığı.") CancellationParty cancelledByParty,
        Instant cancelledAt,
        @Schema(description = "Uygulanan kesinti oranı, exact decimal string.", example = "0.2500") String feeRate,
        @Schema(description = "Kesinti tutarı, exact decimal string.", example = "27.50") String feeAmount,
        @Schema(description = "İşverene iade edilen tutar, exact decimal string.", example = "82.50") String refundAmount) {
}
