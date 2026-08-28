package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.dispute.request.ResolveDisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Uyuşmazlık yönetimi", description = "Açık uyuşmazlıkların admin tarafından çözülmesi.")
@RestController
@RequestMapping("/admin/disputes")
public class DisputesController {

    private final DisputeService disputeService;
    private final DisputeMapper disputeMapper;

    public DisputesController(DisputeService disputeService, DisputeMapper disputeMapper) {
        this.disputeService = disputeService;
        this.disputeMapper = disputeMapper;
    }

    @Operation(summary = "Açık uyuşmazlıklar",
            description = "Sayfalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uyuşmazlıklar listelendi")
    })
    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<DisputeDto>>> pending(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = disputeService.getPending(pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, disputeMapper.toDisputeDtoList(page.getContent())),
                DisputeMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Uyuşmazlığı çöz",
            description = "RELEASE_TO_WORKER parayı işçiye çıkarır, REFUND_EMPLOYER işverene iade eder, DISMISSED emanete dokunmaz. Kısmi paylaşım yoktur: bir atama ya işçiye çıkar ya iade edilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uyuşmazlık çözüldü"),
            @ApiResponse(responseCode = "404", description = "Uyuşmazlık bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Uyuşmazlık zaten çözülmüş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<SuccessDataResult<DisputeDto>> resolve(
            @PathVariable UUID disputeId, @RequestBody @Valid ResolveDisputeDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeMapper.toDisputeDto(disputeService.resolveDispute(
                        disputeId, request.getResolution(), request.getNote())),
                DisputeMessages.RESOLVED, HttpStatus.OK));
    }
}
