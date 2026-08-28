package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.PayoutMessages;
import com.exskylab.koala.core.dtos.payout.request.RefundRequestDto;
import com.exskylab.koala.core.dtos.payout.response.PayoutDto;
import com.exskylab.koala.core.mappers.PayoutMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Ödeme çıkışları", description = "İşçiye çıkış, iade ve kendi ödemelerim.")
@RestController
@RequestMapping
public class PayoutsController {

    private final PayoutService payoutService;
    private final PayoutMapper payoutMapper;
    private final SecurityService securityService;

    public PayoutsController(PayoutService payoutService, PayoutMapper payoutMapper,
                             SecurityService securityService) {
        this.payoutService = payoutService;
        this.payoutMapper = payoutMapper;
        this.securityService = securityService;
    }

    @Operation(summary = "Bana yapılan ödemeler")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ödemeler listelendi")
    })
    @GetMapping("/users/me/payouts")
    public ResponseEntity<SuccessDataResult<List<PayoutDto>>> myPayouts() {
        List<PayoutDto> payouts = payoutMapper.toPayoutDtoList(
                payoutService.getPayoutsForWorker(securityService.getAuthenticatedUserId()));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                payouts, PayoutMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Emanetteki parayı işçiye çıkar",
            description = "Yalnızca atama COMPLETED ise çalışır ve açık uyuşmazlık varsa reddedilir. Aynı atama için ikinci kez çağrılırsa yeni kayıt açılmaz. Sağlayıcı mutabakatı senkron yapılmaz; kuyruğa alınıp üstel geri çekilmeyle denenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Çıkış kaydı oluşturuldu"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Atama tamamlanmamış, para emanette değil ya da açık uyuşmazlık var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/admin/job-assignments/{assignmentId}/release")
    public ResponseEntity<SuccessDataResult<PayoutDto>> release(@PathVariable UUID assignmentId) {
        PayoutDto payout = payoutMapper.toPayoutDto(payoutService.releaseToWorker(assignmentId));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                payout, PayoutMessages.RELEASED, HttpStatus.OK));
    }

    @Operation(summary = "İşverene iade et",
            description = "Yalnızca para emanetteyken mümkündür; işçiye çıkmış para iade edilemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İade kaydı oluşturuldu"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Para emanette değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/admin/job-assignments/{assignmentId}/refund")
    public ResponseEntity<SuccessResult> refund(@PathVariable UUID assignmentId,
                                                @RequestBody @Valid RefundRequestDto request) {
        payoutService.refundEmployer(assignmentId, request.getAmount(), request.getReason());

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessResult(
                PayoutMessages.REFUNDED, HttpStatus.OK));
    }
}
