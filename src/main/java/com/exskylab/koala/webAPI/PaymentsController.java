package com.exskylab.koala.webAPI;

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

    @GetMapping("/job-assignments/{assignmentId}/payments")
    public ResponseEntity<SuccessDataResult<List<PaymentAttemptDto>>> listAttempts(
            @PathVariable UUID assignmentId) {

        List<PaymentAttemptDto> attempts =
                paymentMapper.toPaymentAttemptDtoList(paymentService.getAttemptsForAssignment(assignmentId));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                attempts, PaymentMessages.ATTEMPTS_RETRIEVED, HttpStatus.OK));
    }
}
