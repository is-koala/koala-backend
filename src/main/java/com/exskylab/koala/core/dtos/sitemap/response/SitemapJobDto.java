package com.exskylab.koala.core.dtos.sitemap.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record SitemapJobDto(
        UUID id,
        @Schema(types = {"string", "null"}, format = "date-time",
                description = "İlanın authoritative son değişiklik anı; eski kayıtlarda null olabilir.")
        Instant updatedAt) {
}
