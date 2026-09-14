package com.exskylab.koala.core.dtos.review.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(UUID id,
                        @Schema(types = {"string", "null"}, format = "uuid",
                                description = "Değerlendirmenin yazıldığı atama. Yalnız değerlendirmeyi bırakanın aldığı "
                                        + "POST yanıtında dolu; herkese açık listede null, çünkü iki profildeki aynı "
                                        + "atama kimliği anonim yazarı açığa çıkarır.")
                        UUID jobAssignmentId,
                        SafeUserDto author, int rating, String comment, boolean anonymous,
                        Instant createdAt) {
}
