package com.exskylab.koala.core.dtos.jobAssignment.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Görüntüleyen kullanıcının bu atamaya yazdığı değerlendirme; henüz yazmadıysa null.")
public record MyReviewDto(UUID id,
                          @Schema(description = "Verilen puan, 1-5.") int rating,
                          @Schema(types = {"string", "null"}, description = "Yorum; yazılmadıysa null.")
                          String comment,
                          Instant createdAt) {
}
