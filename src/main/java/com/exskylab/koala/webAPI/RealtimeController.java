package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.business.abstracts.PresenceService;
import com.exskylab.koala.business.abstracts.RealtimeCredentialService;
import com.exskylab.koala.core.constants.MessagingMessages;
import com.exskylab.koala.core.constants.RealtimeMessages;
import com.exskylab.koala.core.dtos.realtime.response.PresenceDto;
import com.exskylab.koala.core.dtos.realtime.response.RealtimeCredentialDto;
import com.exskylab.koala.core.realtime.RealtimePublisher;
import com.exskylab.koala.core.security.RealtimeCredential;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Gerçek zamanlı", description = "Presence sorguları ve yazıyor bildirimi.")
@Controller
public class RealtimeController {

    private final MessagingService messagingService;
    private final PresenceService presenceService;
    private final RealtimePublisher realtimePublisher;
    private final RealtimeCredentialService realtimeCredentialService;

    public RealtimeController(MessagingService messagingService, PresenceService presenceService,
                              RealtimePublisher realtimePublisher,
                              RealtimeCredentialService realtimeCredentialService) {
        this.messagingService = messagingService;
        this.presenceService = presenceService;
        this.realtimePublisher = realtimePublisher;
        this.realtimeCredentialService = realtimeCredentialService;
    }

    @Operation(summary = "Kısa ömürlü gerçek zamanlı bağlantı kimliği",
            description = "Yalnız STOMP CONNECT için geçerli, mevcut oturuma bağlı kimlik döndürür.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bağlantı kimliği üretildi"),
            @ApiResponse(responseCode = "401", description = "Oturum geçersiz")
    })
    @PostMapping("/users/me/realtime-credential")
    @ResponseBody
    public ResponseEntity<SuccessDataResult<RealtimeCredentialDto>> issueCredential() {
        RealtimeCredential credential = realtimeCredentialService.issue();
        RealtimeCredentialDto response = new RealtimeCredentialDto(
                credential.token(), credential.expiresAt());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new SuccessDataResult<>(response,
                        RealtimeMessages.CREDENTIAL_ISSUED, HttpStatus.OK));
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable UUID conversationId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        messagingService.assertParticipant(conversationId, principal.id());
        realtimePublisher.typing(conversationId, principal.id());
    }

    @Operation(summary = "Kullanıcının çevrimiçi durumu",
            description = "Anlık durum ve son görülme zamanı.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Durum döndürüldü")
    })
    @GetMapping("/users/{userId}/presence")
    @ResponseBody
    public ResponseEntity<SuccessDataResult<PresenceDto>> presenceOf(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                new PresenceDto(userId, presenceService.isOnline(userId), presenceService.lastSeenAt(userId)),
                MessagingMessages.PRESENCE_FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "Toplu presence sorgusu",
            description = "Sohbet listesi için tek istekte birden çok kullanıcının durumu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Durumlar döndürüldü")
    })
    @PostMapping("/presence/lookup")
    @ResponseBody
    public ResponseEntity<SuccessDataResult<List<PresenceDto>>> presenceOfMany(
            @RequestBody List<UUID> userIds) {

        List<PresenceDto> statuses = presenceService.statusOf(userIds).entrySet().stream()
                .map(entry -> new PresenceDto(entry.getKey(), entry.getValue(),
                        presenceService.lastSeenAt(entry.getKey())))
                .toList();

        return ResponseEntity.ok(new SuccessDataResult<>(
                statuses, MessagingMessages.PRESENCE_FETCHED, HttpStatus.OK));
    }
}
