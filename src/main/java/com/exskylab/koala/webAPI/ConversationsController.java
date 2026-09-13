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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Mesajlaşma", description = "İlana bağlı konuşmalar ve mesajlar.")
@RestController
@RequestMapping
public class ConversationsController {

    private final MessagingService messagingService;
    private final MessagingMapper messagingMapper;

    public ConversationsController(MessagingService messagingService, MessagingMapper messagingMapper) {
        this.messagingService = messagingService;
        this.messagingMapper = messagingMapper;
    }

    @Operation(summary = "Konuşmalarım",
            description = "Her satır son mesaj metnini ve okunmamış sayısını taşır; satır başına ek istek gerekmez. Sayfalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Konuşmalar listelendi"),
            @ApiResponse(responseCode = "400", description = "Sıralama alanı desteklenmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/conversations")
    public ResponseEntity<SuccessDataResult<PageDto<ConversationDto>>> myConversations(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                messagingService.getMyConversations(pageable),
                MessagingMessages.CONVERSATIONS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İlan üzerinden konuşma aç",
            description = "Konuşma açmak için işçinin bu ilandaki atamasının ödemesi emanette olmalıdır "
                    + "(HELD_IN_ESCROW ya da RELEASED_TO_WORKER); ödeme yoksa 403 MESSAGING_PAYMENT_REQUIRED. "
                    + "Atama iptal/ret edilmiş, iade edilmiş ya da iş bitiminden sonraki pencere kapanmışsa 409 "
                    + "MESSAGING_WINDOW_CLOSED. Aynı ilanda aynı işçiyle ikinci konuşma açılmaz, mevcut olan döner. "
                    + "workerId verilmezse çağıran işçi sayılır; işveren tarafı (ilan sahibi ya da şirket yetkilisi) "
                    + "workerId vermek zorundadır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Konuşma açıldı ya da mevcut konuşma döndürüldü"),
            @ApiResponse(responseCode = "403",
                    description = "Ödeme henüz emanette değil (MESSAGING_PAYMENT_REQUIRED) ya da taraflardan biri "
                            + "diğerini engellemiş (MESSAGING_BLOCKED)",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404",
                    description = "İlan bulunamadı ya da çağıran ilanın işveren tarafı da işçisi de değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409",
                    description = "Mesajlaşma kapalı: atama iptal/ret/iade edildi ya da iş bitimi penceresi doldu",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/jobs/{jobId}/conversation")
    public ResponseEntity<SuccessDataResult<ConversationDto>> openConversation(
            @PathVariable UUID jobId, @RequestParam(required = false) UUID workerId) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                messagingMapper.toConversationDto(messagingService.openConversation(jobId, workerId), 0L, false, java.util.List.of()),
                MessagingMessages.CONVERSATIONS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Konuşmanın mesajları",
            description = "Taraf olmayan kullanıcı 404 alır; konuşmanın varlığı sızmaz. Sayfalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesajlar listelendi"),
            @ApiResponse(responseCode = "404", description = "Konuşma bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<SuccessDataResult<PageDto<MessageDto>>> messages(
            @PathVariable UUID conversationId, @ParameterObject @PageableDefault(size = 30) Pageable pageable) {

        var page = messagingService.getMessages(conversationId, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, messagingMapper.toMessageDtoList(page.getContent())),
                MessagingMessages.MESSAGES_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Mesaj gönder",
            description = "Engelleme iki yönlü kontrol edilir: taraflardan biri diğerini engellediyse mesaj gönderilemez. "
                    + "İkili sohbette işçinin ataması ödenmemişse 403 MESSAGING_PAYMENT_REQUIRED; atama kapandıysa "
                    + "ya da iş bitimi penceresi dolduysa 409 MESSAGING_WINDOW_CLOSED. Mesaj gerçek zamanlı olarak "
                    + "konuşma topic ve katılımcıların kişisel kuyruğuna yayınlanır.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mesaj gönderildi"),
            @ApiResponse(responseCode = "403",
                    description = "Taraflardan biri diğerini engellemiş ya da ödeme henüz emanette değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Konuşma bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Mesajlaşma kapalı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<SuccessDataResult<MessageDto>> send(
            @PathVariable UUID conversationId, @RequestBody @Valid SendMessageDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                messagingMapper.toMessageDto(messagingService.sendMessage(conversationId, request.getBody())),
                MessagingMessages.MESSAGE_SENT, HttpStatus.CREATED));
    }

    @Operation(summary = "Konuşmayı okundu işaretle",
            description = "Okunmamış mesajları okundu yapar ve READ olayını iki tarafın kişisel kuyruğuna yayınlar; kullanıcının diğer cihazındaki rozet de düşer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Okundu işaretlendi"),
            @ApiResponse(responseCode = "404", description = "Konuşma bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<SuccessResult> markRead(@PathVariable UUID conversationId) {
        messagingService.markRead(conversationId);
        return ResponseEntity.ok(new SuccessResult(MessagingMessages.CONVERSATION_READ, HttpStatus.OK));
    }
}
