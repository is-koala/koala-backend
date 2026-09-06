package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.core.constants.JobApplicationMessages;
import com.exskylab.koala.core.dtos.jobApplication.request.CreateJobApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicantDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDto;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.core.constants.JobMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.request.JobSearchCriteriaDto;
import com.exskylab.koala.core.dtos.job.response.JobDetailDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.constants.GalleryMessages;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;

import java.util.UUID;
import jakarta.validation.Valid;
import com.exskylab.koala.entities.JobApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.exskylab.koala.core.security.UserPrincipal;

import java.util.List;

@Tag(name = "İlanlar", description = "İlan arama, detay, kapatma, galeri ve başvurular.")
@RestController
@RequestMapping("/jobs")
public class JobsController {

    private final JobService jobService;
    private final JobApplicationService jobApplicationService;
    private final JobApplicationMapper jobApplicationMapper;
    private final JobMapper jobMapper;


    public JobsController(JobService jobService, JobApplicationService jobApplicationService, JobApplicationMapper jobApplicationMapper, JobMapper jobMapper) {
        this.jobService = jobService;
        this.jobApplicationService = jobApplicationService;
        this.jobApplicationMapper = jobApplicationMapper;
        this.jobMapper = jobMapper;
    }



    @SecurityRequirements
    @Operation(summary = "İlan ara",
            description = "Metin, kategori, il, ilçe, tür, ücret aralığı, sigorta ve deneyim şartı ile süzer. Harita için minLatitude, maxLatitude, minLongitude ve maxLongitude ile sınırlayıcı kutu verilebilir. Yalnızca OPEN durumundaki ilanlar döner. Kart alanı coverImageUrl galerinin kapak görselinden gelir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlanlar listelendi"),
            @ApiResponse(responseCode = "400", description = "Sıralama alanı desteklenmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<JobSummaryDto>>> searchJobs(
            JobSearchCriteriaDto criteria,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal viewer) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.search(criteria, pageable, viewer == null ? null : viewer.id()),
                JobMessages.JOBS_LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "İlan detayı",
            description = "Özet değil detay döner. Tam adres yalnız ilan sahibi, etkin ataması bulunan işçi veya adresi herkese açılmış şirket ilanı için verilir; tamamlanan/iptal edilen atamalarda adres yeniden gizlenir. Diğer çağrılarda il/ilçe/mahalle ve yaklaşık koordinat döner. addressPrecision alanı kesinliği belirtir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlan döndürüldü"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{jobId}")
    public ResponseEntity<SuccessDataResult<JobDetailDto>> getJob(@PathVariable UUID jobId,
                                                                  @AuthenticationPrincipal UserPrincipal viewer) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.getJobDetail(jobId, viewer == null ? null : viewer.id()),
                JobMessages.JOB_RETRIEVED, HttpStatus.OK));
    }

    @Operation(summary = "İlanı kapat",
            description = "Yalnızca ilan sahibi ya da ilanın şirketinin yetkilisi kapatabilir. Yalnızca OPEN durumundan geçilebilir. Yetkisiz kullanıcı 404 alır, ilanın varlığı sızmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlan kapatıldı"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "İlan zaten açık değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{jobId}/close")
    public ResponseEntity<SuccessResult> closeJob(@PathVariable UUID jobId) {
        jobService.closeJob(jobId);
        return ResponseEntity.ok(new SuccessResult(JobMessages.JOB_CLOSED, HttpStatus.OK));
    }

    @Operation(summary = "İlanı iptal et",
            description = "İlanı CANCELLED durumuna çeker ve başvuranlara bildirim gönderir. Kapatmadan farkı: kapatma yalnızca yeni başvuruyu durdurur, iptal ilanı geçersiz kılar. Devam eden bir iş ataması varsa iptal edilemez; o durumda atamanın kendi iptal akışı kullanılır. Kayıt silinmez, başvuru geçmişi korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlan iptal edildi"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "İlan zaten iptal edilmiş ya da devam eden atama var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<SuccessResult> cancelJob(@PathVariable UUID jobId) {
        jobService.cancelJob(jobId);
        return ResponseEntity.ok(new SuccessResult(JobMessages.JOB_CANCELLED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "İlan galerisi",
            description = "Sıra numarasına göre döner, kapak görseli cover bayrağıyla işaretlidir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Galeri listelendi"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{jobId}/images")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> jobImages(@PathVariable UUID jobId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.getJobGallery(jobId), GalleryMessages.IMAGE_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İlan galerisine görsel yükle",
            description = "İlk yüklenen görsel kendiliğinden kapak olur. İlan başına azami görsel sayısı upload ayarlarından gelir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Görsel eklendi"),
            @ApiResponse(responseCode = "400", description = "Dosya boş, çok büyük ya da desteklenmeyen türde",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Galeri sınırı aşıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping(value = "/{jobId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> addJobImage(
            @PathVariable UUID jobId, @RequestPart("image") MultipartFile image) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                jobService.addJobImage(jobId, image), GalleryMessages.IMAGE_ADDED, HttpStatus.CREATED));
    }

    @Operation(summary = "İlan galerisini toptan değiştir",
            description = "Gövdedeki imageId ya ilanın mevcut galerisinden ya da ilanın şirketinin galerisinden seçilebilir; böylece şirket mekânını bir kez yükleyip her ilanda yeniden seçer. Listede olmayan kayıtlar galeriden düşer. Aynı görsel iki kez listelenemez ve en fazla bir kapak seçilebilir; hiçbiri işaretlenmezse ilk sıradaki kapak yapılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Galeri güncellendi"),
            @ApiResponse(responseCode = "400", description = "Aynı görsel iki kez listelendi ya da birden fazla kapak seçildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Görsel bu ilanda ve şirket galerisinde yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/{jobId}/images")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> updateJobGallery(
            @PathVariable UUID jobId, @RequestBody @Valid UpdateGalleryDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.replaceJobGallery(jobId, request), GalleryMessages.IMAGE_UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Görseli ilan galerisinden çıkar",
            description = "Kapak silinirse sıradaki görsel kapak olur. Medya kaydı silinmez, yalnızca ilişki kalkar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Görsel kaldırıldı"),
            @ApiResponse(responseCode = "404", description = "Görsel bu galeride yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/{jobId}/images/{imageId}")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> removeJobImage(
            @PathVariable UUID jobId, @PathVariable UUID imageId) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.removeJobImage(jobId, imageId), GalleryMessages.IMAGE_REMOVED, HttpStatus.OK));
    }

    @Operation(summary = "İlana başvur",
            description = "Yalnızca OPEN ilan başvuru alır; kapatılan ya da iptal edilen ilana yeni başvuru gelmez. Kullanıcı kendi ilanına başvuramaz, aynı ilana ikinci başvuru açılmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Başvuru oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Kendi ilanına başvurulamaz, zaten başvurulmuş ya da başvuru şartları eksik",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "İlan başvuruya kapalı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{jobId}/applications")
    public ResponseEntity<SuccessDataResult<JobApplicationDto>> applyToJob(@PathVariable UUID jobId, @RequestBody @Valid CreateJobApplicationDto jobsJobIdApplicationsPostResponseDto){

        JobApplicationDto jobApplicationsDto = jobApplicationService.applyToJob(jobId, jobsJobIdApplicationsPostResponseDto);


        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<JobApplicationDto>(
                jobApplicationsDto,
                JobApplicationMessages.JOB_APPLICATION_CREATED_SUCCESS,
                HttpStatus.CREATED
        ));

    }


    @Operation(summary = "İlana gelen başvurular",
            description = "Yalnızca ilan sahibi ya da şirket yetkilisi görebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Başvurular listelendi"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{jobId}/applications")
    public ResponseEntity<SuccessDataResult<List<JobApplicantDto>>> getJobApplicationById(@PathVariable UUID jobId){

        List<JobApplicantDto> jobApplicationsDto = jobApplicationService.getJobApplicationsByIdJobId(jobId);

        return ResponseEntity.ok(new SuccessDataResult<List<JobApplicantDto>>(
                jobApplicationsDto,
                JobApplicationMessages.JOB_APPLICATIONS_RETRIEVED_SUCCESS,
                HttpStatus.OK
        ));


    }


}
