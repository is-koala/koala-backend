package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.sitemap.response.SitemapManifestDto;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapShardDto;

import java.util.UUID;

public interface SitemapManifestService {

    SitemapManifestDto getManifest();

    SitemapShardDto getShard(UUID version, int shard);
}
