package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.sitemap.response.SitemapJobDto;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapManifestDto;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapShardDto;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapShardMetadataDto;
import com.exskylab.koala.dataAccess.SitemapManifestDao;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SitemapMapper {

    SitemapJobDto toJobDto(SitemapManifestDao.SitemapEntryRecord entry);

    List<SitemapJobDto> toJobDtos(List<SitemapManifestDao.SitemapEntryRecord> entries);

    @Mapping(target = "number", source = "number")
    @Mapping(target = "entryCount", source = "entryCount")
    SitemapShardMetadataDto toShardMetadataDto(int number, int entryCount);

    @Mapping(target = "version", source = "manifest.version")
    @Mapping(target = "createdAt", source = "manifest.createdAt")
    @Mapping(target = "expiresAt", source = "manifest.expiresAt")
    @Mapping(target = "entryCount", source = "manifest.entryCount")
    @Mapping(target = "shardSize", source = "manifest.shardSize")
    @Mapping(target = "shards", source = "shards")
    SitemapManifestDto toManifestDto(SitemapManifestDao.ManifestRecord manifest,
                                     List<SitemapShardMetadataDto> shards);

    @Mapping(target = "version", source = "version")
    @Mapping(target = "shard", source = "shard")
    @Mapping(target = "entries", source = "entries")
    SitemapShardDto toShardDto(UUID version, int shard, List<SitemapJobDto> entries);
}
