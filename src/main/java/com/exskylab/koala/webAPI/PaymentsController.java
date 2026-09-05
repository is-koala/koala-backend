package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.PricingService;
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.dtos.payment.request.PricingQuoteRequestDto;
import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
import com.exskylab.koala.core.dtos.payment.response.PaymentAttemptDto;
import com.exskylab.koala.core.helpers.PaymentReturnUrlBuilder;
import com.exskylab.koala.core.mappers.PaymentMapper;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.UUID;

@Tag(name = "Ödemeler", description = "Sağlayıcı geri dönüşü ve ödeme denemeleri.")
@RestController
@RequestMapping
@Slf4j
public class PaymentsController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    private final PaymentReturnUrlBuilder paymentReturnUrlBuilder;
    private final PricingService pricingService;

    public PaymentsController(PaymentService paymentService, PaymentMapper paymentMapper,
                              PaymentReturnUrlBuilder paymentReturnUrlBuilder, PricingService pricingService) {
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
        this.paymentReturnUrlBuilder = paymentReturnUrlBuilder;
        this.pricingService = pricingService;
    }

    @Operation(summary = "İlan öncesi fiyat dökümü",
            description = "Çalışana ödenecek ücret için backend yuvarlamasıyla hizmet bedeli ve işveren toplamını hesaplar. Bu önizleme ilan yayınlanırken tekrar hesaplanıp snapshot olarak kilitlenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fiyat dökümü hesaplandı"),
            @ApiResponse(responseCode = "400", description = "Çalışan ücreti geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "401", description = "Oturum gerekli",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/payment-quotes")
    public ResponseEntity<SuccessDataResult<PricingBreakdownDto>> quote(
            @RequestBody @Valid PricingQuoteRequestDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                pricingService.quote(request.workerPayout()),
                PaymentMessages.QUOTE_CALCULATED,
                HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Iyzico geri dönüşü",
            description = "Gövdeye güvenilmez: yalnızca token okunur ve sonuç sunucudan sunucuya sağlayıcıya sorulur. Bu yüzden imza doğrulaması uygulanmaz. İdempotanttır; zaten başarılı bir ödemeye ikinci callback gelirse sağlayıcıya hiç gidilmez ve kayıt değişmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Geri dönüş işlendi"),
            @ApiResponse(responseCode = "404", description = "Bu token ile ödeme kaydı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/payments/callback")
    public RedirectView handleProviderCallback(@RequestParam("token") String token) {
        log.info("Payment callback received.");

        Payment payment = paymentService.completeFromCallback(token);
        boolean succeeded = payment.getStatus() == PaymentAttemptStatus.SUCCEEDED;

        String target = paymentReturnUrlBuilder.resultUrl(
                succeeded, payment.getJobAssignment().getId());

        return new RedirectView(target);
    }

    @Operation(summary = "Atamanın ödeme denemeleri",
            description = "Her deneme ayrı kayıttır; başarısız deneme yeniden denenebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Denemeler listelendi"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/job-assignments/{assignmentId}/payments")
    public ResponseEntity<SuccessDataResult<List<PaymentAttemptDto>>> listAttempts(
            @PathVariable UUID assignmentId) {

        List<PaymentAttemptDto> attempts =
                paymentMapper.toPaymentAttemptDtoList(paymentService.getAttemptsForAssignment(assignmentId));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                attempts, PaymentMessages.ATTEMPTS_RETRIEVED, HttpStatus.OK));
    }
}
