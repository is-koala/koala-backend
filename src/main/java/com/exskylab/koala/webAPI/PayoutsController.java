package com.exskylab.koala.webAPI;

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

@RestController
@RequestMapping("/payouts")
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

    @GetMapping("/me")
    public ResponseEntity<SuccessDataResult<List<PayoutDto>>> myPayouts() {
        List<PayoutDto> payouts = payoutMapper.toPayoutDtoList(
                payoutService.getPayoutsForWorker(securityService.getAuthenticatedUserId()));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                payouts, PayoutMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/job-assignments/{assignmentId}/release")
    public ResponseEntity<SuccessDataResult<PayoutDto>> release(@PathVariable UUID assignmentId) {
        PayoutDto payout = payoutMapper.toPayoutDto(payoutService.releaseToWorker(assignmentId));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                payout, PayoutMessages.RELEASED, HttpStatus.OK));
    }

    @PostMapping("/job-assignments/{assignmentId}/refund")
    public ResponseEntity<SuccessResult> refund(@PathVariable UUID assignmentId,
                                                @RequestBody @Valid RefundRequestDto request) {
        payoutService.refundEmployer(assignmentId, request.getAmount(), request.getReason());

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessResult(
                PayoutMessages.REFUNDED, HttpStatus.OK));
    }
}
