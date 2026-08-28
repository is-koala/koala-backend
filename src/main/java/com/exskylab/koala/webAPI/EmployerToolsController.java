package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.JobTemplateService;
import com.exskylab.koala.business.abstracts.TalentPoolService;
import com.exskylab.koala.core.constants.JobTemplateMessages;
import com.exskylab.koala.core.constants.TalentPoolMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.jobTemplate.request.CreateJobTemplateDto;
import com.exskylab.koala.core.dtos.jobTemplate.response.JobTemplateDto;
import com.exskylab.koala.core.dtos.talent.request.SaveTalentPoolEntryDto;
import com.exskylab.koala.core.dtos.talent.response.TalentPoolEntryDto;
import com.exskylab.koala.core.mappers.JobTemplateMapper;
import com.exskylab.koala.core.mappers.TalentPoolMapper;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "İşveren araçları", description = "Yetenek havuzu ve ilan şablonları.")
@RestController
@RequestMapping("/users/me")
public class EmployerToolsController {

    private final TalentPoolService talentPoolService;
    private final JobTemplateService jobTemplateService;
    private final TalentPoolMapper talentPoolMapper;
    private final JobTemplateMapper jobTemplateMapper;

    public EmployerToolsController(TalentPoolService talentPoolService, JobTemplateService jobTemplateService,
                                   TalentPoolMapper talentPoolMapper, JobTemplateMapper jobTemplateMapper) {
        this.talentPoolService = talentPoolService;
        this.jobTemplateService = jobTemplateService;
        this.talentPoolMapper = talentPoolMapper;
        this.jobTemplateMapper = jobTemplateMapper;
    }

    @Operation(summary = "Yetenek havuzum")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Havuz listelendi")
    })
    @GetMapping("/talent-pool")
    public ResponseEntity<SuccessDataResult<PageDto<TalentPoolEntryDto>>> talentPool(
            @PageableDefault(size = 20) Pageable pageable) {

        var page = talentPoolService.getMyTalentPool(pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, talentPoolMapper.toTalentPoolEntryDtoList(page.getContent())),
                TalentPoolMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İşçiyi havuza ekle",
            description = "İşveren başına tekildir; aynı işçiyi ikinci kez eklemek mevcut kaydın notunu günceller. Kendini eklemek reddedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İşçi havuza eklendi"),
            @ApiResponse(responseCode = "400", description = "Kendinizi ekleyemezsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/talent-pool/{workerId}")
    public ResponseEntity<SuccessDataResult<TalentPoolEntryDto>> addToTalentPool(
            @PathVariable UUID workerId, @RequestBody(required = false) @Valid SaveTalentPoolEntryDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                talentPoolMapper.toTalentPoolEntryDto(talentPoolService.addWorker(workerId, request)),
                TalentPoolMessages.ADDED, HttpStatus.OK));
    }

    @Operation(summary = "İşçiyi havuzdan çıkar",
            description = "Havuzda olmayan birini çıkarmak sessizce geçer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İşçi çıkarıldı")
    })
    @DeleteMapping("/talent-pool/{workerId}")
    public ResponseEntity<Result> removeFromTalentPool(@PathVariable UUID workerId) {
        talentPoolService.removeWorker(workerId);
        return ResponseEntity.ok(new SuccessResult(TalentPoolMessages.REMOVED, HttpStatus.OK));
    }

    @Operation(summary = "İlan şablonlarım")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şablonlar listelendi")
    })
    @GetMapping("/job-templates")
    public ResponseEntity<SuccessDataResult<List<JobTemplateDto>>> jobTemplates() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobTemplateMapper.toJobTemplateDtoList(jobTemplateService.getMyTemplates()),
                JobTemplateMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İlandan şablon çıkar",
            description = "Şablon sıfırdan yazılmaz, mevcut bir ilanın alanları kopyalanır. İlanın sahibi ya da şirketinin yetkilisi olmayan biri şablon çıkaramaz. Tarih, adres ve durum kopyalanmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Şablon oluşturuldu"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/job-templates")
    public ResponseEntity<SuccessDataResult<JobTemplateDto>> createJobTemplate(
            @RequestBody @Valid CreateJobTemplateDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                jobTemplateMapper.toJobTemplateDto(jobTemplateService.createFromJob(request)),
                JobTemplateMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Şablon detayı")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şablon döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Şablon bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/job-templates/{templateId}")
    public ResponseEntity<SuccessDataResult<JobTemplateDto>> jobTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobTemplateMapper.toJobTemplateDto(jobTemplateService.getMyTemplate(templateId)),
                JobTemplateMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şablonu sil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şablon silindi"),
            @ApiResponse(responseCode = "404", description = "Şablon bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/job-templates/{templateId}")
    public ResponseEntity<Result> deleteJobTemplate(@PathVariable UUID templateId) {
        jobTemplateService.deleteTemplate(templateId);
        return ResponseEntity.ok(new SuccessResult(JobTemplateMessages.DELETED, HttpStatus.OK));
    }
}
