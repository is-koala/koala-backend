package com.exskylab.koala.core.dtos.cancellation.response;

import java.math.BigDecimal;

public record CancellationPreviewDto(BigDecimal feeRate, BigDecimal feeAmount, BigDecimal refundAmount,
                                     long noticeMinutes, boolean cancellableNow) {
}
