package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.constants.BadgeMessages;
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

@RestController
public class BadgesController {

    private final BadgeService badgeService;
    private final BadgeMapper badgeMapper;

    public BadgesController(BadgeService badgeService, BadgeMapper badgeMapper) {
        this.badgeService = badgeService;
        this.badgeMapper = badgeMapper;
    }

    @GetMapping("/users/{userId}/badges")
    public ResponseEntity<SuccessDataResult<List<BadgeDto>>> badgesOf(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeDtoList(badgeService.getBadgesOf(userId)),
                BadgeMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/reference/badges")
    public ResponseEntity<SuccessDataResult<List<BadgeDto>>> catalog() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toCatalogDtoList(badgeService.getCatalog()),
                BadgeMessages.CATALOG_LISTED, HttpStatus.OK));
    }

    @GetMapping("/admin/badges")
    public ResponseEntity<SuccessDataResult<List<BadgeAdminDto>>> allBadges() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDtoList(badgeService.getAllForAdministration()),
                BadgeMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/admin/badges")
    public ResponseEntity<SuccessDataResult<BadgeAdminDto>> createBadge(
            @RequestBody @Valid SaveBadgeDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDto(badgeService.createBadge(request)),
                BadgeMessages.CREATED, HttpStatus.CREATED));
    }

    @PutMapping("/admin/badges/{badgeId}")
    public ResponseEntity<SuccessDataResult<BadgeAdminDto>> updateBadge(
            @PathVariable UUID badgeId, @RequestBody @Valid SaveBadgeDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDto(badgeService.updateBadge(badgeId, request)),
                BadgeMessages.UPDATED, HttpStatus.OK));
    }

    @PutMapping(value = "/admin/badges/{badgeId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<BadgeAdminDto>> setIcon(
            @PathVariable UUID badgeId, @RequestPart("icon") MultipartFile icon) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDto(badgeService.setIcon(badgeId, icon)),
                BadgeMessages.ICON_UPDATED, HttpStatus.OK));
    }

    @PostMapping("/admin/badges/{badgeId}/activation")
    public ResponseEntity<SuccessDataResult<BadgeAdminDto>> setActive(
            @PathVariable UUID badgeId, @RequestParam boolean active) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                badgeMapper.toBadgeAdminDto(badgeService.setActive(badgeId, active)),
                BadgeMessages.ACTIVE_CHANGED, HttpStatus.OK));
    }
}
