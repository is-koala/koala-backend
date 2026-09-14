package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.legal.request.RejectDeletionDto;
import com.exskylab.koala.core.dtos.legal.response.AccountDeletionDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.mappers.LegalMapper;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.AccountDeletionStatus;
import com.exskylab.koala.entities.StaffPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Hesap silme yönetimi")
@RestController
@RequestMapping("/admin/account-deletions")
public class AccountDeletionController {

    private final LegalService legalService;
    private final LegalMapper legalMapper;
    private final MessageResolver messageResolver;

    public AccountDeletionController(LegalService legalService, LegalMapper legalMapper,
                                     MessageResolver messageResolver) {
        this.legalService = legalService;
        this.legalMapper = legalMapper;
        this.messageResolver = messageResolver;
    }

    @Operation(summary = "Hesap silme taleplerini listele",
            description = "status verilmezse tüm durumlar döner. query (2-100 karakter, kırpılır; kısa ya da boş değer "
                    + "süzmez) talep sahibinin e-postasında büyük-küçük harf ve Türkçe karakter duyarsız arar; status "
                    + "süzgeciyle kesişir; 100 karakteri aşarsa 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hesap silme talepleri listelendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz sayfalama veya durum değeri ya da query 100 karakteri aşıyor")
    })
    @GetMapping
    @RequiresPermission(StaffPermission.USER_READ_PII)
    public ResponseEntity<SuccessDataResult<PageDto<AccountDeletionDto>>> list(
            @RequestParam(required = false) AccountDeletionStatus status,
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        var page = legalService.getDeletionRequests(status, query, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, legalMapper.toAccountDeletionDtoList(page.getContent(), messageResolver)),
                LegalMessages.DELETIONS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Hesap silme talebi",
            description = "Kuyruk satırıyla aynı AccountDeletionDto. Ret ayrı uçtan; bu GET yalnız okur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hesap silme talebi getirildi"),
            @ApiResponse(responseCode = "404", description = "Hesap silme talebi bulunamadı")
    })
    @GetMapping("/{requestId}")
    @RequiresPermission(StaffPermission.USER_READ_PII)
    public ResponseEntity<SuccessDataResult<AccountDeletionDto>> get(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toAccountDeletionDto(legalService.getDeletionRequest(requestId), messageResolver),
                LegalMessages.DELETION_FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "Hesap silme talebini gerekçeyle reddet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hesap silme talebi reddedildi"),
            @ApiResponse(responseCode = "404", description = "Hesap silme talebi bulunamadı"),
            @ApiResponse(responseCode = "409", description = "Talep daha önce işlendi")
    })
    @PostMapping("/{requestId}/reject")
    @RequiresPermission(value = StaffPermission.USER_DELETION_MANAGE, action = "user.deletion.reject")
    public ResponseEntity<SuccessDataResult<AccountDeletionDto>> reject(
            @PathVariable UUID requestId, @RequestBody @Valid RejectDeletionDto body,
            @AuthenticationPrincipal StaffPrincipal actor) {
        var deletion = legalService.rejectDeletionRequest(actor, requestId, body.reason());
        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toAccountDeletionDto(deletion, messageResolver),
                LegalMessages.DELETION_REJECTED, HttpStatus.OK));
    }
}
