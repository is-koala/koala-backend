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
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.dtos.payment.response.PaymentAttemptDto;
import com.exskylab.koala.core.mappers.PaymentMapper;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Tag(name = "Ödemeler", description = "Sağlayıcı geri dönüşü ve ödeme denemeleri.")
@RestController
@RequestMapping
@Slf4j
public class PaymentsController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    private final AppProperties appProperties;

    public PaymentsController(PaymentService paymentService, PaymentMapper paymentMapper,
                              AppProperties appProperties) {
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
        this.appProperties = appProperties;
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

        String target = appProperties.frontendUrl() + "/payment/result"
                + "?status=" + (succeeded ? "success" : "failed")
                + "&assignmentId=" + URLEncoder.encode(
                        payment.getJobAssignment().getId().toString(), StandardCharsets.UTF_8);

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
