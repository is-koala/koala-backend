package com.exskylab.koala.core.dtos.dispute.request;

import com.exskylab.koala.core.constants.DisputeMessages;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EvidenceScrubRequestDto {

    @Parameter(description = "Bu çağrıda işlenecek en fazla kanıt sayısı, 1-100; aralık dışı değer 400 döner.",
            example = "25")
    @NotNull(message = DisputeMessages.EVIDENCE_SCRUB_LIMIT_RANGE)
    @Min(value = 1, message = DisputeMessages.EVIDENCE_SCRUB_LIMIT_RANGE)
    @Max(value = 100, message = DisputeMessages.EVIDENCE_SCRUB_LIMIT_RANGE)
    private Integer limit = 25;

    @Parameter(description = "true ise daha önce başarısız olmuş (failed) kanıtlar yeniden denenir.", example = "false")
    private Boolean retryFailed = false;

    public boolean retriesFailed() {
        return Boolean.TRUE.equals(retryFailed);
    }
}
