package com.exskylab.koala.core.dtos.user.response;

import com.exskylab.koala.core.dtos.badge.response.BadgeDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicProfileDto(UUID id, String firstName, String lastName, String username,
                               String profilePictureUrl, String jobTitle, String about,
                               String cityName, String districtName, String cvUrl,
                               @Schema(description = "İşçi olarak tamamladığı iş sayısı.")
                               long completedJobCount,
                               @Schema(description = "Hakkındaki değerlendirmelerin ortalaması; değerlendirme yoksa 0. GET /users/{id}/rating-summary ile aynı kaynaktan.")
                               BigDecimal averageRating,
                               @Schema(description = "Hakkındaki değerlendirme sayısı; yoksa 0.")
                               int reviewCount,
                               @Schema(description = "Kazandığı rozetler; GET /users/{id}/badges ile aynı liste.")
                               List<BadgeDto> badges) {
}
