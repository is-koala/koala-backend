package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.dispute.request.ResolveDisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/disputes")
public class DisputesController {

    private final DisputeService disputeService;
    private final DisputeMapper disputeMapper;

    public DisputesController(DisputeService disputeService, DisputeMapper disputeMapper) {
        this.disputeService = disputeService;
        this.disputeMapper = disputeMapper;
    }

    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<DisputeDto>>> pending(
            @PageableDefault(size = 20) Pageable pageable) {

        var page = disputeService.getPending(pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, disputeMapper.toDisputeDtoList(page.getContent())),
                DisputeMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<SuccessDataResult<DisputeDto>> resolve(
            @PathVariable UUID disputeId, @RequestBody @Valid ResolveDisputeDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeMapper.toDisputeDto(disputeService.resolveDispute(
                        disputeId, request.getResolution(), request.getNote())),
                DisputeMessages.RESOLVED, HttpStatus.OK));
    }
}
