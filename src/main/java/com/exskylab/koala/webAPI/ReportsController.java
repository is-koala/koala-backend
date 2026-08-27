package com.exskylab.koala.webAPI;

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
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/reports")
public class ReportsController {

    private final ReportService reportService;
    private final ReportMapper reportMapper;

    public ReportsController(ReportService reportService, ReportMapper reportMapper) {
        this.reportService = reportService;
        this.reportMapper = reportMapper;
    }

    @PostMapping
    public ResponseEntity<SuccessDataResult<ReportDto>> create(@RequestBody @Valid CreateReportDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                reportMapper.toReportDto(reportService.createReport(
                        request.getReportedUserId(), request.getJobId(), request.getMessageId(),
                        request.getReason(), request.getDescription())),
                ReportMessages.CREATED, HttpStatus.CREATED));
    }

    @GetMapping("/me")
    public ResponseEntity<SuccessDataResult<PageDto<ReportDto>>> mine(
            @PageableDefault(size = 20) Pageable pageable) {

        var page = reportService.getMyReports(pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, reportMapper.toReportDtoList(page.getContent())),
                ReportMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<ReportDto>>> byStatus(
            @RequestParam ReportStatus status, @PageableDefault(size = 20) Pageable pageable) {

        var page = reportService.getByStatus(status, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, reportMapper.toReportDtoList(page.getContent())),
                ReportMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/{reportId}/review")
    public ResponseEntity<SuccessDataResult<ReportDto>> review(
            @PathVariable UUID reportId, @RequestBody @Valid ReviewReportDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                reportMapper.toReportDto(reportService.review(
                        reportId, request.getStatus(), request.getNote())),
                ReportMessages.REVIEWED, HttpStatus.OK));
    }
}
