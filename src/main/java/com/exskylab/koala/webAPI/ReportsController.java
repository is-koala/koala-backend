package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
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
import com.exskylab.koala.core.dtos.report.response.ReportDto;
import com.exskylab.koala.core.mappers.ReportMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.ReportStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            description = "Hedef kullanıcı, ilan, mesaj ya da görsel olabilir; en az biri zorunludur. Kendini şikayet etmek reddedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Şikayet oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Hedef verilmedi ya da kendinizi şikayet edemezsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Hedef bulunamadı",
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

    @Operation(summary = "Şikayetleri duruma göre listele",
            description = "Yalnızca ADMIN rolüne açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şikayetler listelendi")
    })
    @RequiresPermission(StaffPermission.CONTENT_MODERATE)
    @GetMapping("/admin/reports")
    public ResponseEntity<SuccessDataResult<PageDto<ReportDto>>> byStatus(
            @RequestParam ReportStatus status, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = reportService.getByStatus(status, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, reportMapper.toReportDtoList(page.getContent())),
                ReportMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şikayeti sonuçlandır",
            description = "Sonuçlanmış bir şikayet tekrar sonuçlandırılamaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şikayet sonuçlandırıldı"),
            @ApiResponse(responseCode = "404", description = "Şikayet bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Şikayet zaten sonuçlandırılmış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.CONTENT_MODERATE, action = "report.review")
    @PostMapping("/admin/reports/{reportId}/review")
    public ResponseEntity<SuccessDataResult<ReportDto>> review(
            @PathVariable UUID reportId, @RequestBody @Valid ReviewReportDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                reportMapper.toReportDto(reportService.review(
                        reportId, request.getStatus(), request.getNote())),
                ReportMessages.REVIEWED, HttpStatus.OK));
    }
}
