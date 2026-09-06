package com.exskylab.koala.core.dtos.cancellation.response;

public record CancellationPreviewDto(String feeRate, String feeAmount, String refundAmount,
                                     long noticeMinutes, boolean cancellableNow) {
}
