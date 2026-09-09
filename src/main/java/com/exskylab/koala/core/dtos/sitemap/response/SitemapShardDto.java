package com.exskylab.koala.core.dtos.sitemap.response;

import java.util.List;
import java.util.UUID;

public record SitemapShardDto(UUID version, int shard, List<SitemapJobDto> entries) {
}
