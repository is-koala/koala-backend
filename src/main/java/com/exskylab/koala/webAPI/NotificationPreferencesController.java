package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.NotificationPreferenceService;
import com.exskylab.koala.core.constants.NotificationPreferenceMessages;
import com.exskylab.koala.core.dtos.notificationPreference.request.UnsubscribeDto;
import com.exskylab.koala.core.dtos.notificationPreference.request.UpdateNotificationPreferencesDto;
import com.exskylab.koala.core.dtos.notificationPreference.response.NotificationPreferenceDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Bildirim tercihleri",
        description = "Kategori ve kanal bazlı bildirim tercihleri ile ticari ileti izni.")
@RestController
public class NotificationPreferencesController {

    private final NotificationPreferenceService notificationPreferenceService;
    private final ClientIpResolver clientIpResolver;

    public NotificationPreferencesController(NotificationPreferenceService notificationPreferenceService,
                                             ClientIpResolver clientIpResolver) {
        this.notificationPreferenceService = notificationPreferenceService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Bildirim tercihlerimi getir",
            description = "Her kategori ve kanal için çözümlenmiş durumu döndürür. Satırı olmayan kombinasyon "
                    + "kategorinin varsayılanına düşer: alarmlar açık, ticari iletiler kapalı gelir. "
                    + "`editable=false` olan satırlar işlemsel bildirimlerdir ve kapatılamaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tercihler listelendi")
    })
    @GetMapping("/users/me/notification-preferences")
    public ResponseEntity<SuccessDataResult<List<NotificationPreferenceDto>>> myPreferences() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                notificationPreferenceService.getMyPreferences(),
                NotificationPreferenceMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Bildirim tercihlerimi güncelle",
            description = "Gövdedeki her satır bir kategori-kanal kararıdır; gönderilmeyen kombinasyonlara "
                    + "dokunulmaz. İşlemsel bir kategoriyi kapatmaya çalışmak 400 döner. Ticari ileti "
                    + "kategorilerindeki her karar an, kanal ve IP ile birlikte rıza kaydına yazılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tercihler güncellendi"),
            @ApiResponse(responseCode = "400", description = "İşlemsel kategori kapatılamaz ya da kanal desteklenmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/users/me/notification-preferences")
    public ResponseEntity<SuccessDataResult<List<NotificationPreferenceDto>>> updateMyPreferences(
            @RequestBody @Valid UpdateNotificationPreferencesDto request, HttpServletRequest httpRequest) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                notificationPreferenceService.updateMyPreferences(
                        request, ClientContext.from(httpRequest, clientIpResolver)),
                NotificationPreferenceMessages.UPDATED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Abonelikten çık",
            description = "E-postadaki tek tıklık bağlantı bu ucu çağırır; **giriş gerektirmez**. İmzalı token "
                    + "yalnızca kendi taşıdığı kategori ve kanalı kapatır, başka hiçbir tercihe dokunamaz ve "
                    + "hiçbir kanalı açamaz. Aynı bağlantıya ikinci kez tıklamak zararsızdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlgili kanal kapatıldı"),
            @ApiResponse(responseCode = "400", description = "Token geçersiz ya da süresi dolmuş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/notification-preferences/unsubscribe")
    public ResponseEntity<Result> unsubscribe(@RequestBody @Valid UnsubscribeDto request,
                                              HttpServletRequest httpRequest) {

        notificationPreferenceService.unsubscribe(
                request.getToken(), ClientContext.from(httpRequest, clientIpResolver));

        return ResponseEntity.ok(new SuccessResult(
                NotificationPreferenceMessages.UNSUBSCRIBED, HttpStatus.OK));
    }
}
