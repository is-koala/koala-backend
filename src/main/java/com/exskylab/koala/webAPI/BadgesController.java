package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.entities.StaffPermission;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.constants.BadgeMessages;
import com.exskylab.koala.core.constants.UploadDocs;
import com.exskylab.koala.core.dtos.badge.request.SaveBadgeDto;
import com.exskylab.koala.core.dtos.badge.response.BadgeAdminDto;
import com.exskylab.koala.core.dtos.badge.response.BadgeDto;
import com.exskylab.koala.core.mappers.BadgeMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Rozetler", description = "Rozet kataloğu, kullanıcı rozetleri ve rozet yönetimi.")
@RestController
public class BadgesController {

    private final BadgeService badgeService;
    private final BadgeMapper badgeMapper;

    public BadgesController(BadgeService badgeService, BadgeMapper badgeMapper) {
        this.badgeService = badgeService;
        this.badgeMapper = badgeMapper;
    }

    @SecurityRequirements
    @Operation(summary = "Kullanıcının rozetleri",
            description = "Kazanılan rozetler ve kazanılma tarihleri. Kazanılan rozet geri alınmaz. Herkese açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rozetler listelendi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/users/{userId}/badges")
    public ResponseEntity<SuccessDataResult<List<BadgeDto>>> badgesOf(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeDtoList(badgeService.getBadgesOf(userId)),
                BadgeMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Aktif rozet kataloğu",
            description = "Verilmeye devam eden rozetler. Pasifleştirilmiş rozetler burada görünmez ama sahiplerinde durur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Katalog döndürüldü")
    })
    @GetMapping("/reference/badges")
    public ResponseEntity<SuccessDataResult<List<BadgeDto>>> catalog() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toCatalogDtoList(badgeService.getCatalog()),
                BadgeMessages.CATALOG_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Tüm rozetler",
            description = "Pasifleştirilmiş olanlar dahil.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rozetler listelendi")
    })
    @RequiresPermission(StaffPermission.BADGE_MANAGE)
    @GetMapping("/admin/badges")
    public ResponseEntity<SuccessDataResult<List<BadgeAdminDto>>> allBadges() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDtoList(badgeService.getAllForAdministration()),
                BadgeMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Rozet oluştur",
            description = "Kod, ad, açıklama, sıra, kural tipi ve eşikleriyle yeni bir rozet tanımlar. Kod benzersizdir ve sonradan değiştirilemez. Mevcut bir kural tipini kullanan rozet deploy gerektirmez; görsel ayrı uçtan yüklenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rozet oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Kural için gereken eşik eksik ya da kod biçimi geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Bu kod zaten kullanılıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.BADGE_MANAGE, action = "badge.create")
    @PostMapping("/admin/badges")
    public ResponseEntity<SuccessDataResult<BadgeAdminDto>> createBadge(
            @RequestBody @Valid SaveBadgeDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDto(badgeService.createBadge(request)),
                BadgeMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Rozeti güncelle",
            description = "Ad, açıklama, sıra ve eşikler değiştirilebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rozet güncellendi"),
            @ApiResponse(responseCode = "400", description = "Kural için gereken eşik eksik",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Rozet bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.BADGE_MANAGE, action = "badge.update")
    @PutMapping("/admin/badges/{badgeId}")
    public ResponseEntity<SuccessDataResult<BadgeAdminDto>> updateBadge(
            @PathVariable UUID badgeId, @RequestBody @Valid SaveBadgeDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDto(badgeService.updateBadge(badgeId, request)),
                BadgeMessages.UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Rozet görselini değiştir",
            description = "Görsel depoya yazılır ve yanıtlarda iconUrl olarak döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Görsel güncellendi"),
            @ApiResponse(responseCode = "400", description = "Dosya desteklenmeyen türde. " + UploadDocs.IMAGE_TYPES,
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Rozet bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "413", description = "Tek dosya 10 MiB ya da toplam multipart gövdesi 42 MiB sınırını aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.BADGE_MANAGE, action = "badge.icon.set")
    @PutMapping(value = "/admin/badges/{badgeId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<BadgeAdminDto>> setIcon(
            @PathVariable UUID badgeId, @RequestPart("icon") MultipartFile icon) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDto(badgeService.setIcon(badgeId, icon)),
                BadgeMessages.ICON_UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Rozeti aktifleştir ya da pasifleştir",
            description = "Pasifleştirilen rozet o andan sonra kimseye verilmez ama sahiplerinde kalır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Durum güncellendi"),
            @ApiResponse(responseCode = "404", description = "Rozet bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.BADGE_MANAGE, action = "badge.activation.set")
    @PostMapping("/admin/badges/{badgeId}/activation")
    public ResponseEntity<SuccessDataResult<BadgeAdminDto>> setActive(
            @PathVariable UUID badgeId, @RequestParam boolean active) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDto(badgeService.setActive(badgeId, active)),
                BadgeMessages.ACTIVE_CHANGED, HttpStatus.OK));
    }
}
