package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.SitemapManifestService;
import com.exskylab.koala.core.constants.SitemapMessages;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapManifestDto;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapShardDto;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Sitemap", description = "Sabit ilan snapshot'ları ve bunların sınırlı shard projection'ları.")
@RestController
@RequestMapping("/sitemap/jobs")
public class SitemapController {

    private final SitemapManifestService sitemapManifestService;

    public SitemapController(SitemapManifestService sitemapManifestService) {
        this.sitemapManifestService = sitemapManifestService;
    }

    @Operation(summary = "İlan sitemap manifestini getir",
            description = "TTL boyunca değişmeyen bir version, kayıt sayısı ve en fazla 50.000 kayıtlık shard metadata'sı döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manifest döndürüldü"),
            @ApiResponse(responseCode = "429", description = "Sitemap okuma sınırı aşıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @SecurityRequirements
    @GetMapping("/manifest")
    public ResponseEntity<SuccessDataResult<SitemapManifestDto>> manifest() {
        return ResponseEntity.ok(new SuccessDataResult<>(sitemapManifestService.getManifest(),
                SitemapMessages.MANIFEST_RETRIEVED, HttpStatus.OK));
    }

    @Operation(summary = "Manifest shard'ını getir",
            description = "Yalnız id ve authoritative nullable updatedAt alanlarını, manifestte sabitlenen sırayla döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shard döndürüldü"),
            @ApiResponse(responseCode = "400", description = "Version veya shard biçimi geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Manifest süresi dolmuş, bilinmiyor veya shard yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Sitemap okuma sınırı aşıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @SecurityRequirements
    @GetMapping("/manifests/{version}/shards/{shard}")
    public ResponseEntity<SuccessDataResult<SitemapShardDto>> shard(
            @PathVariable UUID version, @PathVariable int shard) {
        return ResponseEntity.ok(new SuccessDataResult<>(sitemapManifestService.getShard(version, shard),
                SitemapMessages.SHARD_RETRIEVED, HttpStatus.OK));
    }
}
