package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.core.constants.MessagingMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.messaging.request.SendMessageDto;
import com.exskylab.koala.core.dtos.messaging.response.ConversationDto;
import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.core.dtos.messaging.response.MessageDto;
import com.exskylab.koala.core.mappers.MessagingMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/conversations")
public class ConversationsController {

    private final MessagingService messagingService;
    private final MessagingMapper messagingMapper;

    public ConversationsController(MessagingService messagingService, MessagingMapper messagingMapper) {
        this.messagingService = messagingService;
        this.messagingMapper = messagingMapper;
    }

    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<ConversationDto>>> myConversations(
            @PageableDefault(size = 20) Pageable pageable) {

        var page = messagingService.getMyConversations(pageable);
        var unread = messagingService.unreadCounts(page.getContent().stream().map(Conversation::getId).toList());

        var content = page.getContent().stream()
                .map(conversation -> messagingMapper.toConversationDto(
                        conversation, unread.getOrDefault(conversation.getId(), 0L)))
                .toList();

        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, content),
                MessagingMessages.CONVERSATIONS_LISTED, HttpStatus.OK));
    }

    @PostMapping("/jobs/{jobId}")
    public ResponseEntity<SuccessDataResult<ConversationDto>> openConversation(
            @PathVariable UUID jobId, @RequestParam(required = false) UUID workerId) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                messagingMapper.toConversationDto(messagingService.openConversation(jobId, workerId), 0L),
                MessagingMessages.CONVERSATIONS_LISTED, HttpStatus.OK));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<SuccessDataResult<PageDto<MessageDto>>> messages(
            @PathVariable UUID conversationId, @PageableDefault(size = 30) Pageable pageable) {

        var page = messagingService.getMessages(conversationId, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, messagingMapper.toMessageDtoList(page.getContent())),
                MessagingMessages.MESSAGES_LISTED, HttpStatus.OK));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<SuccessDataResult<MessageDto>> send(
            @PathVariable UUID conversationId, @RequestBody @Valid SendMessageDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                messagingMapper.toMessageDto(messagingService.sendMessage(conversationId, request.getBody())),
                MessagingMessages.MESSAGE_SENT, HttpStatus.CREATED));
    }

    @PostMapping("/{conversationId}/read")
    public ResponseEntity<SuccessResult> markRead(@PathVariable UUID conversationId) {
        messagingService.markRead(conversationId);
        return ResponseEntity.ok(new SuccessResult(MessagingMessages.CONVERSATION_READ, HttpStatus.OK));
    }
}
