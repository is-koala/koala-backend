package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.ReportService;
import com.exskylab.koala.core.constants.ReportMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.report.request.CreateReportDto;
import com.exskylab.koala.core.dtos.report.request.ReviewReportDto;
import com.exskylab.koala.core.dtos.report.response.AdminReportDetailDto;
import com.exskylab.koala.core.dtos.report.response.AdminReportDto;
import com.exskylab.koala.core.dtos.report.response.ReportDto;
import com.exskylab.koala.core.mappers.ReportMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.ReportReason;
import com.exskylab.koala.entities.ReportStatus;
import com.exskylab.koala.entities.ReportTargetType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Şikayetler", description = "Şikayet oluşturma ve yönetimi.")
@RestController
@RequestMapping
public class ReportsController {

    private final ReportService reportService;
    private final ReportMapper reportMapper;

    public ReportsController(ReportService reportService, ReportMapper reportMapper) {
        this.reportService = reportService;
        this.reportMapper = reportMapper;
    }

    @Operation(summary = "Şikayet oluştur",
            description = "Hedef kullanıcı, ilan, mesaj ya da görsel olabilir; en az biri zorunludur. "
                    + "Mesaj hedefinde kullanıcı kalıcı gönderenden türetilir. Kendini şikayet etmek reddedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Şikayet oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Hedef verilmedi ya da kendinizi şikayet edemezsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Hedef bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Aynı mesaj farklı içerikle daha önce şikayet edildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/reports")
    public ResponseEntity<SuccessDataResult<ReportDto>> create(@RequestBody @Valid CreateReportDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                reportMapper.toReportDto(reportService.createReport(
                        request.getReportedUserId(), request.getJobId(), request.getMessageId(),
                        request.getImageId(), request.getReason(), request.getDescription())),
                ReportMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Şikayetlerim",
            description = "Sayfalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şikayetler listelendi")
    })
    @GetMapping("/reports/me")
    public ResponseEntity<SuccessDataResult<PageDto<ReportDto>>> mine(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = reportService.getMyReports(pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, reportMapper.toReportDtoList(page.getContent())),
                ReportMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şikayet kuyruğu",
            description = "Süzgeçlerin hepsi isteğe bağlıdır; status verilmezse tüm durumlar döner. "
                    + "targetType şikayetin hedefine göre daraltır, reportedUserId aynı kullanıcı hakkındaki "
                    + "şikayetleri gruplar. Satırlar şikayet edeni, kararı vereni ve şikayet edilenin "
                    + "kuyruğun tamamı üzerinden hesaplanmış toplam şikayet sayısını taşır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şikayetler listelendi"),
            @ApiResponse(responseCode = "400", description = "Bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.CONTENT_MODERATE)
    @GetMapping("/admin/reports")
    public ResponseEntity<SuccessDataResult<PageDto<AdminReportDto>>> queue(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) UUID reportedUserId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = reportService.searchForModeration(status, reason, targetType, reportedUserId, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent()),
                ReportMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şikayet detayı",
            description = "Tek bir şikayetin tüm bağlamı: şikayet eden, şikayet edilen, kararı veren, "
                    + "şikayet edilenin toplam şikayet sayısı ve hedefin okunabilir içeriği "
                    + "(ilan başlığı, mesaj metni, görsel URL'i). İçerik kullanıcı tarafından yazılmıştır ve "
                    + "yalnızca bu uçta döner; kuyruk satırları içerik taşımaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şikayet getirildi"),
            @ApiResponse(responseCode = "404", description = "Şikayet bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.CONTENT_MODERATE)
    @GetMapping("/admin/reports/{reportId}")
    public ResponseEntity<SuccessDataResult<AdminReportDetailDto>> detail(@PathVariable UUID reportId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                reportService.getForModeration(reportId), ReportMessages.DETAIL, HttpStatus.OK));
    }

    @Operation(summary = "Şikayeti sonuçlandır", description = "Sonuçlanmış bir şikayet tekrar "
            + "sonuçlandırılamaz, karar gerekçesi zorunludur ve yanıt kuyruk satırının tamamını döndürür. "
            + "reviewedBy yalnızca köprü (marketplace ROLE_ADMIN) kararlarında dolar; personel kimliğiyle "
            + "verilen kararda boş kalır, kararı kimin verdiğini denetim kaydı söyler.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şikayet sonuçlandırıldı"),
            @ApiResponse(responseCode = "400", description = "Karar gerekçesi boş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Şikayet bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Şikayet zaten sonuçlandırılmış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.CONTENT_MODERATE, action = "report.review")
    @PostMapping("/admin/reports/{reportId}/review")
    public ResponseEntity<SuccessDataResult<AdminReportDto>> review(
            @PathVariable UUID reportId, @RequestBody @Valid ReviewReportDto request,
            @AuthenticationPrincipal StaffPrincipal actor) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                reportService.review(actor, reportId, request.getStatus(), request.getNote()),
                ReportMessages.REVIEWED, HttpStatus.OK));
    }
}
