package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.CvService;
import com.exskylab.koala.business.abstracts.EducationService;
import com.exskylab.koala.business.abstracts.SavedJobService;
import com.exskylab.koala.core.constants.CvMessages;
import com.exskylab.koala.core.constants.EducationMessages;
import com.exskylab.koala.core.constants.SavedJobMessages;
import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.core.dtos.education.response.EducationDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.mappers.EducationMapper;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.companyFollow.response.FollowedCompanyDto;
import com.exskylab.koala.core.mappers.JobMapper;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Profil", description = "Takip edilen şirketler, kaydedilen ilanlar, özgeçmiş ve eğitim.")
@RestController
@RequestMapping("/users/me")
public class UserProfileController {

    private final CompanyService companyService;
    private final SavedJobService savedJobService;
    private final CvService cvService;
    private final EducationService educationService;
    private final JobMapper jobMapper;
    private final EducationMapper educationMapper;
    private final MediaUrlResolver mediaUrlResolver;

    public UserProfileController(CompanyService companyService,
                                 SavedJobService savedJobService, CvService cvService,
                                 EducationService educationService, JobMapper jobMapper,
                                 EducationMapper educationMapper, MediaUrlResolver mediaUrlResolver) {
        this.companyService = companyService;
        this.savedJobService = savedJobService;
        this.cvService = cvService;
        this.educationService = educationService;
        this.jobMapper = jobMapper;
        this.educationMapper = educationMapper;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @Operation(summary = "Takip ettiğim şirketler",
            description = "Sayfalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirketler listelendi")
    })
    @GetMapping("/followed-companies")
    public ResponseEntity<SuccessDataResult<PageDto<FollowedCompanyDto>>> followedCompanies(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.getFollowedCompanies(pageable),
                CompanyMessages.FOLLOWED_COMPANIES_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kaydettiğim ilanlar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlanlar listelendi")
    })
    @GetMapping("/saved-jobs")
    public ResponseEntity<SuccessDataResult<List<JobSummaryDto>>> savedJobs() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobMapper.toJobSummaryDtoList(savedJobService.getMySavedJobs()),
                SavedJobMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İlanı kaydet",
            description = "İdempotanttır, ikinci istek yeni kayıt açmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlan kaydedildi"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/saved-jobs/{jobId}")
    public ResponseEntity<Result> saveJob(@PathVariable UUID jobId) {
        savedJobService.saveJob(jobId);
        return ResponseEntity.ok(new SuccessResult(SavedJobMessages.SAVED, HttpStatus.OK));
    }

    @Operation(summary = "Kaydedilen ilanı çıkar",
            description = "Kaydedilmemiş bir ilanı çıkarmak sessizce geçer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlan çıkarıldı")
    })
    @DeleteMapping("/saved-jobs/{jobId}")
    public ResponseEntity<Result> removeSavedJob(@PathVariable UUID jobId) {
        savedJobService.removeSavedJob(jobId);
        return ResponseEntity.ok(new SuccessResult(SavedJobMessages.REMOVED, HttpStatus.OK));
    }

    @Operation(summary = "Özgeçmiş yükle",
            description = "Yalnızca PDF ve Word kabul edilir. Nesne özel kalır; dönen adres kimlik doğrulaması ister ve kısa ömürlü imzalı adrese yönlendirir, CDN üzerinden servis edilmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Özgeçmiş yüklendi"),
            @ApiResponse(responseCode = "400", description = "Dosya boş, çok büyük ya da desteklenmeyen türde",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/cv")
    public ResponseEntity<SuccessDataResult<String>> uploadCv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                mediaUrlResolver.toFileUrl(cvService.uploadCv(file)),
                CvMessages.UPLOADED, HttpStatus.OK));
    }

    @Operation(summary = "Özgeçmişi sil",
            description = "Kayıt soft delete edilir, depodaki nesne silinmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Özgeçmiş silindi")
    })
    @DeleteMapping("/cv")
    public ResponseEntity<Result> deleteCv() {
        cvService.deleteCv();
        return ResponseEntity.ok(new SuccessResult(CvMessages.DELETED, HttpStatus.OK));
    }

    @Operation(summary = "Eğitim kayıtlarım")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eğitim kayıtları listelendi")
    })
    @GetMapping("/educations")
    public ResponseEntity<SuccessDataResult<List<EducationDto>>> educations() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toEducationDtoList(educationService.getMyEducations()),
                EducationMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Eğitim kaydı ekle",
            description = "Fakülte seçilen üniversiteye, bölüm seçilen fakülteye ait olmalıdır; istemci üç ayrı seçim yapar, tutarlılık sunucuda doğrulanır.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Eğitim kaydı eklendi"),
            @ApiResponse(responseCode = "400", description = "Fakülte ya da bölüm seçilen üst kayda ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Üniversite, fakülte ya da bölüm bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/educations")
    public ResponseEntity<SuccessDataResult<EducationDto>> addEducation(
            @RequestBody @Valid SaveEducationDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                educationMapper.toEducationDto(educationService.addEducation(request)),
                EducationMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Eğitim kaydını güncelle",
            description = "Başkasının kaydına dokunmak 404 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eğitim kaydı güncellendi"),
            @ApiResponse(responseCode = "400", description = "Fakülte ya da bölüm seçilen üst kayda ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kayıt bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/educations/{educationId}")
    public ResponseEntity<SuccessDataResult<EducationDto>> updateEducation(
            @PathVariable UUID educationId, @RequestBody @Valid SaveEducationDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toEducationDto(educationService.updateEducation(educationId, request)),
                EducationMessages.UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Eğitim kaydını sil",
            description = "Başkasının kaydına dokunmak 404 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eğitim kaydı silindi"),
            @ApiResponse(responseCode = "404", description = "Kayıt bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/educations/{educationId}")
    public ResponseEntity<Result> deleteEducation(@PathVariable UUID educationId) {
        educationService.deleteEducation(educationId);
        return ResponseEntity.ok(new SuccessResult(EducationMessages.DELETED, HttpStatus.OK));
    }
}
