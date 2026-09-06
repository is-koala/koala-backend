package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.core.dtos.legal.request.ConsentDecisionDto;
import com.exskylab.koala.core.dtos.legal.request.DeleteAccountRequestDto;
import com.exskylab.koala.core.dtos.legal.response.AgreementVersionDto;
import com.exskylab.koala.core.dtos.legal.response.ConsentRecordDto;
import com.exskylab.koala.core.dtos.legal.response.AccountDeletionDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.mappers.LegalMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Yasal", description = "Sözleşmeler, rıza kayıtları ve hesap silme talebi.")
@RestController
@RequestMapping("/legal")
public class LegalController {

    private final LegalService legalService;
    private final LegalMapper legalMapper;
    private final SecurityService securityService;
    private final ClientIpResolver clientIpResolver;
    private final MessageResolver messageResolver;

    public LegalController(LegalService legalService, LegalMapper legalMapper,
                           SecurityService securityService, ClientIpResolver clientIpResolver,
                           MessageResolver messageResolver) {
        this.legalService = legalService;
        this.legalMapper = legalMapper;
        this.securityService = securityService;
        this.clientIpResolver = clientIpResolver;
        this.messageResolver = messageResolver;
    }

    @SecurityRequirements
    @Operation(summary = "Yürürlükteki sözleşmeler",
            description = "Sözleşmeler sürümlüdür; metin değişince yeni sürüm eklenir ve eskisi emekliye ayrılır. Herkese açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sözleşmeler listelendi")
    })
    @GetMapping("/agreements")
    public ResponseEntity<SuccessDataResult<List<AgreementVersionDto>>> currentAgreements() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toAgreementVersionDtoList(legalService.getCurrentAgreements()),
                LegalMessages.AGREEMENTS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Rıza kayıtlarım",
            description = "Reddedilen onaylar da kayıtlıdır; sormadık ile sorduk reddetti ayrımı denetimde gerekir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rıza kayıtları listelendi")
    })
    @GetMapping("/consents/me")
    public ResponseEntity<SuccessDataResult<List<ConsentRecordDto>>> myConsents() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toConsentRecordDtoList(
                        legalService.getConsentsOfUser(securityService.getAuthenticatedUserId())),
                LegalMessages.CONSENTS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Rıza kaydet",
            description = "Kimin hangi metnin hangi sürümünü, ne zaman, hangi IP ve istemciden onayladığı kaydedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rızalar kaydedildi"),
            @ApiResponse(responseCode = "404", description = "Sözleşme sürümü bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/consents")
    public ResponseEntity<SuccessDataResult<List<ConsentRecordDto>>> recordConsents(
            @RequestBody @Valid List<ConsentDecisionDto> decisions, HttpServletRequest request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toConsentRecordDtoList(legalService.recordConsents(
                        securityService.getAuthenticatedUser(), decisions,
                        ClientContext.from(request, clientIpResolver))),
                LegalMessages.CONSENTS_RECORDED, HttpStatus.OK));
    }

    @Operation(summary = "Hesap silme talebi oluştur",
            description = "Talep kaydedilir. Açık yükümlülük varsa durum yanıtında bekleme gerekçesi görünür; yükümlülük kapandıktan ve bekleme süresi dolduktan sonra anonimleştirme otomatik ilerler.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Talep alındı"),
            @ApiResponse(responseCode = "409", description = "Bekleyen talep zaten var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/account-deletion")
    public ResponseEntity<SuccessDataResult<AccountDeletionDto>> requestAccountDeletion(
            @RequestBody @Valid DeleteAccountRequestDto request) {

        var deletion = legalService.requestAccountDeletion(
                securityService.getAuthenticatedUser(), request.getReason());

        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toAccountDeletionDto(deletion, messageResolver),
                LegalMessages.DELETION_REQUESTED, HttpStatus.OK));
    }

    @Operation(summary = "Hesap silme talebimin durumu")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Talep durumu döndü"),
            @ApiResponse(responseCode = "404", description = "Talep bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/account-deletion")
    public ResponseEntity<SuccessDataResult<AccountDeletionDto>> accountDeletionStatus() {
        var deletion = legalService.getCurrentDeletionRequest(securityService.getAuthenticatedUserId());

        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toAccountDeletionDto(deletion, messageResolver),
                LegalMessages.DELETIONS_LISTED, HttpStatus.OK));
    }
}
