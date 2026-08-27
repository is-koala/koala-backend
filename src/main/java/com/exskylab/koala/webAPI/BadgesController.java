package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.constants.BadgeMessages;
import com.exskylab.koala.core.dtos.badge.response.BadgeDto;
import com.exskylab.koala.core.mappers.BadgeMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
