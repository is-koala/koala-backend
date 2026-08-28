package com.exskylab.koala.webAPI;

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

@RestController
@RequestMapping("/users/me/blocks")
public class UserBlocksController {

    private final MessagingService messagingService;
    private final MessagingMapper messagingMapper;

    public UserBlocksController(MessagingService messagingService, MessagingMapper messagingMapper) {
        this.messagingService = messagingService;
        this.messagingMapper = messagingMapper;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<SuccessResult> block(@PathVariable UUID userId,
                                               @RequestBody @Valid BlockUserDto request) {
        messagingService.block(userId, request.getReason());
        return ResponseEntity.ok(new SuccessResult(MessagingMessages.USER_BLOCKED, HttpStatus.OK));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<SuccessResult> unblock(@PathVariable UUID userId) {
        messagingService.unblock(userId);
        return ResponseEntity.ok(new SuccessResult(MessagingMessages.USER_UNBLOCKED, HttpStatus.OK));
    }

    @GetMapping
    public ResponseEntity<SuccessDataResult<List<UserBlockDto>>> myBlocks() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                messagingMapper.toUserBlockDtoList(messagingService.getMyBlocks()),
                MessagingMessages.BLOCKS_LISTED, HttpStatus.OK));
    }
}
