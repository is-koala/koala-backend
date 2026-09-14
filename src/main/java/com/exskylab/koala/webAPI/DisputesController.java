package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.DisputeStatus;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.dtos.admin.request.CreatedAtRangeDto;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.dispute.request.ResolveDisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.StaffDisputeEvidenceDto;
import com.exskylab.koala.core.helpers.SignedFileResponses;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.entities.DisputeEvidence;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Uyuşmazlık yönetimi", description = "Açık uyuşmazlıkların admin tarafından çözülmesi.")
@RestController
@RequestMapping("/admin/disputes")
public class DisputesController {

    private final DisputeService disputeService;
    private final DisputeMapper disputeMapper;
    private final SignedFileResponses signedFileResponses;

    public DisputesController(DisputeService disputeService, DisputeMapper disputeMapper,
                              SignedFileResponses signedFileResponses) {
        this.disputeService = disputeService;
        this.disputeMapper = disputeMapper;
        this.signedFileResponses = signedFileResponses;
    }

    @Operation(summary = "Uyuşmazlık kuyruğu",
            description = "status verilmezse yalnız OPEN ve UNDER_REVIEW döner. "
                    + "status=RESOLVED kapalı kayıtları (DISMISSED çözümü dahil) sayfalar. "
                    + "Tutarlar DisputeDto'da yoktur; GET /admin/job-assignments/{id} (payment.read) otoriter kaynaktır. "
                    + "query (2-100 karakter, kırpılır; kısa ya da boş değer süzmez) uyuşmazlık id'si, atama id'si ve "
                    + "tarafların (çalışan, ilan sahibi, açan) ad-soyadı ile e-postasında büyük-küçük harf ve Türkçe karakter "
                    + "duyarsız arar; status süzgeciyle kesişir; 100 karakteri aşarsa 400. userId verilirse yalnız o "
                    + "kullanıcının taraf olduğu (çalışan, ilan sahibi ya da açan) uyuşmazlıklar döner. from ve to "
                    + "uyuşmazlığın oluşturulma anını (createdAt) süzer, ikisi de dahildir ve dilimli ISO-8601 an ister; "
                    + "yalnız biri verilebilir; status, query ve userId ile kesişir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uyuşmazlıklar listelendi"),
            @ApiResponse(responseCode = "400", description = "query 100 karakteri aşıyor, tarih dilimsiz ya da bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.DISPUTE_RESOLVE)
    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<DisputeDto>>> list(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID userId,
            @ParameterObject CreatedAtRangeDto createdAt,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = disputeService.searchForStaff(status, query, userId, createdAt.toRange(), pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, disputeMapper.toStaffDisputeDtoList(page.getContent())),
                DisputeMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Uyuşmazlık detayı",
            description = "Kuyruk satırıyla aynı DisputeDto. Tutarlar yoktur; emanet GET /admin/job-assignments/{id} "
                    + "(payment.read) otoriter kaynaktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uyuşmazlık getirildi"),
            @ApiResponse(responseCode = "404", description = "Uyuşmazlık bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.DISPUTE_RESOLVE)
    @GetMapping("/{disputeId}")
    public ResponseEntity<SuccessDataResult<DisputeDto>> dispute(@PathVariable UUID disputeId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeMapper.toStaffDisputeDto(disputeService.getForStaff(disputeId)),
                DisputeMessages.FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "Uyuşmazlığı çöz",
            description = "RELEASE_TO_WORKER parayı işçiye çıkarır, REFUND_EMPLOYER işverene iade eder, DISMISSED emanete dokunmaz. Kısmi paylaşım yoktur: bir atama ya işçiye çıkar ya iade edilir. Para hareketi doğuran iki sonuç dispute.resolve'a ek olarak sonucun yönüne göre payout.release ya da payment.refund iznini de ister; izin yoksa 403 döner ve uyuşmazlığın durumuna hiç bakılmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uyuşmazlık çözüldü"),
            @ApiResponse(responseCode = "403", description = "Sonucun gerektirdiği finansal izin yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Uyuşmazlık bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Uyuşmazlık zaten çözülmüş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.DISPUTE_RESOLVE, action = "dispute.resolve")
    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<SuccessDataResult<DisputeDto>> resolve(
            @PathVariable UUID disputeId, @RequestBody @Valid ResolveDisputeDto request,
            @AuthenticationPrincipal StaffPrincipal actor) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeMapper.toStaffDisputeDto(disputeService.resolveDispute(
                        actor, disputeId, request.getResolution(), request.getNote())),
                DisputeMessages.RESOLVED, HttpStatus.OK));
    }

    @Operation(summary = "Uyuşmazlığın kanıtları",
            description = "Kuyruktaki bir uyuşmazlığın kanıt metadatası. Yanıt depo anahtarı ya da kalıcı adres taşımaz; indirme ayrı bir uçtan yapılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kanıtlar listelendi"),
            @ApiResponse(responseCode = "404", description = "Uyuşmazlık bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.DISPUTE_RESOLVE)
    @GetMapping("/{disputeId}/evidence")
    public ResponseEntity<SuccessDataResult<List<StaffDisputeEvidenceDto>>> evidence(@PathVariable UUID disputeId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeService.getEvidenceForStaff(disputeId),
                DisputeMessages.EVIDENCE_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kanıt indir",
            description = "Depo imzalı adres destekliyorsa kısa ömürlü imzalı adrese yönlendirir, desteklemiyorsa baytları akıtır. Tekil okuma olduğu için denetim kaydına girer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dosya akıtıldı"),
            @ApiResponse(responseCode = "302", description = "Kısa ömürlü imzalı adrese yönlendirildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kanıt bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.DISPUTE_RESOLVE, action = "dispute.evidence.download")
    @GetMapping("/{disputeId}/evidence/{evidenceId}/download")
    public ResponseEntity<?> downloadEvidence(@PathVariable UUID disputeId, @PathVariable UUID evidenceId) {
        DisputeEvidence evidence = disputeService.getEvidenceForStaffDownload(disputeId, evidenceId);
        return signedFileResponses.attachment(evidence.getStorageKey(), evidence.getOriginalFileName());
    }
}
