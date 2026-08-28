package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.core.constants.MessagingMessages;
import com.exskylab.koala.core.dtos.messaging.request.BlockUserDto;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.exskylab.koala.core.dtos.messaging.response.UserBlockDto;
import com.exskylab.koala.core.mappers.MessagingMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import java.util.List;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Engelleme", description = "Kullanıcı engelleme ve engel kaldırma.")
@RestController
@RequestMapping("/users/me/blocks")
public class UserBlocksController {

    private final MessagingService messagingService;
    private final MessagingMapper messagingMapper;

    public UserBlocksController(MessagingService messagingService, MessagingMapper messagingMapper) {
        this.messagingService = messagingService;
        this.messagingMapper = messagingMapper;
    }

    @Operation(summary = "Kullanıcıyı engelle",
            description = "Engelleme iki yönlü çalışır: A B yi engellediyse B de A ya yazamaz. İdempotanttır. Kendini engellemek reddedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kullanıcı engellendi"),
            @ApiResponse(responseCode = "400", description = "Kendinizi engelleyemezsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{userId}")
    public ResponseEntity<SuccessResult> block(@PathVariable UUID userId,
                                               @RequestBody @Valid BlockUserDto request) {
        messagingService.block(userId, request.getReason());
        return ResponseEntity.ok(new SuccessResult(MessagingMessages.USER_BLOCKED, HttpStatus.OK));
    }

    @Operation(summary = "Engeli kaldır",
            description = "Engellenmemiş birinin engelini kaldırmak sessizce geçer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Engel kaldırıldı")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<SuccessResult> unblock(@PathVariable UUID userId) {
        messagingService.unblock(userId);
        return ResponseEntity.ok(new SuccessResult(MessagingMessages.USER_UNBLOCKED, HttpStatus.OK));
    }

    @Operation(summary = "Engellediklerim")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Engellenenler listelendi")
    })
    @GetMapping
    public ResponseEntity<SuccessDataResult<List<UserBlockDto>>> myBlocks() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                messagingMapper.toUserBlockDtoList(messagingService.getMyBlocks()),
                MessagingMessages.BLOCKS_LISTED, HttpStatus.OK));
    }
}
