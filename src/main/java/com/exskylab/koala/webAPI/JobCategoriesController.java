package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.JobCategoryService;
import com.exskylab.koala.core.constants.JobCategoryMessages;
import com.exskylab.koala.core.dtos.jobCategory.request.SaveJobCategoryDto;
import com.exskylab.koala.core.dtos.jobCategory.response.JobCategoryDto;
import com.exskylab.koala.core.mappers.JobCategoryMapper;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "İş kategorisi yönetimi",
        description = "İş kategorisi kataloğunun yönetimi. Veritabanı kategorisiz gelir, katalog buradan kurulur.")
@RestController
@RequestMapping("/admin/job-categories")
public class JobCategoriesController {

    private final JobCategoryService jobCategoryService;
    private final JobCategoryMapper jobCategoryMapper;

    public JobCategoriesController(JobCategoryService jobCategoryService, JobCategoryMapper jobCategoryMapper) {
        this.jobCategoryService = jobCategoryService;
        this.jobCategoryMapper = jobCategoryMapper;
    }

    @Operation(summary = "Kategorileri listele",
            description = "Katalogun tamamı, yönetim görünümü.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kategoriler listelendi")
    })
    @GetMapping
    public ResponseEntity<SuccessDataResult<List<JobCategoryDto>>> categories() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobCategoryMapper.toCategoryDtoList(jobCategoryService.getAllCategories()),
                JobCategoryMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kategori oluştur",
            description = "code istemcinin ikon, pin ve renk eşlemesini bağladığı sabittir; verildikten sonra değiştirilmemelidir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Kategori oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Kod ya da ad geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Kod zaten kullanılıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping
    public ResponseEntity<SuccessDataResult<JobCategoryDto>> createCategory(
            @RequestBody @Valid SaveJobCategoryDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                jobCategoryMapper.toCategoryDto(jobCategoryService.createCategory(request)),
                JobCategoryMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Kategoriyi güncelle",
            description = "Ad, kod ve sigorta gerekliliği değiştirilebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kategori güncellendi"),
            @ApiResponse(responseCode = "400", description = "Kod ya da ad geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Kod başka bir kategoride kullanılıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/{categoryId}")
    public ResponseEntity<SuccessDataResult<JobCategoryDto>> updateCategory(
            @PathVariable UUID categoryId, @RequestBody @Valid SaveJobCategoryDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobCategoryMapper.toCategoryDto(jobCategoryService.updateCategory(categoryId, request)),
                JobCategoryMessages.UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Kategoriyi sil",
            description = "İlan, iş alarmı ya da ilan şablonu tarafından kullanılan kategori silinemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kategori silindi"),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Kategori kullanımda",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Result> deleteCategory(@PathVariable UUID categoryId) {
        jobCategoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(new SuccessResult(JobCategoryMessages.DELETED, HttpStatus.OK));
    }
}
