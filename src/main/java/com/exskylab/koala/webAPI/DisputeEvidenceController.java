package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.dtos.dispute.response.DisputeEvidenceDto;
import com.exskylab.koala.core.helpers.SignedFileResponses;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.DisputeEvidence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Uyuşmazlık kanıtları", description = "Uyuşmazlığa taraf olanların kanıt dosyaları.")
@RestController
@RequestMapping("/disputes")
public class DisputeEvidenceController {

    private final DisputeService disputeService;
    private final DisputeMapper disputeMapper;
    private final SignedFileResponses signedFileResponses;

    public DisputeEvidenceController(DisputeService disputeService, DisputeMapper disputeMapper,
                                     SignedFileResponses signedFileResponses) {
        this.disputeService = disputeService;
        this.disputeMapper = disputeMapper;
        this.signedFileResponses = signedFileResponses;
    }

    @Operation(summary = "Kanıt ekle",
            description = "Yalnızca atamanın tarafları ve yalnızca sonuçlanmamış uyuşmazlığa ekleyebilir. Tür uzantı beyaz listesinden, boyut ve uyuşmazlık başına adet sınırı upload.* ayarlarından gelir. Dosya adı depo anahtarına girmez; nesne özel kalır ve yanıt kalıcı adres taşımaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Kanıt eklendi"),
            @ApiResponse(responseCode = "400", description = "Dosya boş, çok büyük ya da türü kabul edilmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Uyuşmazlık bulunamadı ya da tarafı değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Uyuşmazlık sonuçlanmış ya da kanıt sınırı dolmuş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping(value = "/{disputeId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<DisputeEvidenceDto>> attach(
            @PathVariable UUID disputeId, @RequestParam("file") MultipartFile file) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                disputeMapper.toEvidenceDto(disputeService.attachEvidence(disputeId, file)),
                DisputeMessages.EVIDENCE_ATTACHED, HttpStatus.CREATED));
    }

    @Operation(summary = "Kanıtları listele",
            description = "Yalnızca atamanın tarafları görebilir. Yanıt yalnızca metadata taşır; indirme ayrı bir uçtan yapılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kanıtlar listelendi"),
            @ApiResponse(responseCode = "404", description = "Uyuşmazlık bulunamadı ya da tarafı değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{disputeId}/evidence")
    public ResponseEntity<SuccessDataResult<List<DisputeEvidenceDto>>> list(@PathVariable UUID disputeId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeMapper.toEvidenceDtoList(disputeService.getEvidence(disputeId)),
                DisputeMessages.EVIDENCE_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kanıt indir",
            description = "Yalnızca atamanın tarafları indirebilir. Depo imzalı adres destekliyorsa kısa ömürlü imzalı adrese yönlendirir, desteklemiyorsa baytları akıtır. İki yolda da yanıt kalıcı bir adres taşımaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dosya akıtıldı"),
            @ApiResponse(responseCode = "302", description = "Kısa ömürlü imzalı adrese yönlendirildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kanıt bulunamadı ya da tarafı değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{disputeId}/evidence/{evidenceId}/download")
    public ResponseEntity<?> download(@PathVariable UUID disputeId, @PathVariable UUID evidenceId) {
        DisputeEvidence evidence = disputeService.getEvidenceForDownload(disputeId, evidenceId);
        return signedFileResponses.attachment(evidence.getStorageKey(), evidence.getOriginalFileName());
    }
}
