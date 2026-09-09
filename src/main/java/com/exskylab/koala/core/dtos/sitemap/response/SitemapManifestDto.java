package com.exskylab.koala.core.dtos.sitemap.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SitemapManifestDto(UUID version, Instant createdAt, Instant expiresAt,
                                 long entryCount, int shardSize,
                                 List<SitemapShardMetadataDto> shards) {
}
