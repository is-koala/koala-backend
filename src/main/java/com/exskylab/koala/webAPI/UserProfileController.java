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
import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.core.constants.DeviceMessages;
import com.exskylab.koala.core.dtos.device.request.RenameDeviceDto;
import com.exskylab.koala.core.dtos.device.request.SavePushTokenDto;
import com.exskylab.koala.core.dtos.device.response.DeviceDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private final DeviceService deviceService;
    private final SavedJobService savedJobService;
    private final CvService cvService;
    private final EducationService educationService;
    private final JobMapper jobMapper;
    private final EducationMapper educationMapper;
    private final MediaUrlResolver mediaUrlResolver;
    private final ClientIpResolver clientIpResolver;

    public UserProfileController(CompanyService companyService, DeviceService deviceService,
                                 SavedJobService savedJobService, CvService cvService,
                                 EducationService educationService, JobMapper jobMapper,
                                 EducationMapper educationMapper, MediaUrlResolver mediaUrlResolver,
                                 ClientIpResolver clientIpResolver) {
        this.companyService = companyService;
        this.deviceService = deviceService;
        this.savedJobService = savedJobService;
        this.cvService = cvService;
        this.educationService = educationService;
        this.jobMapper = jobMapper;
        this.educationMapper = educationMapper;
        this.mediaUrlResolver = mediaUrlResolver;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Push bildirim token'ını kaydet",
            description = "İstemci FCM token'ını buraya gönderir. Hedef gövdeden alınmaz: token daima **oturum sahibinin** `X-Device-Id` başlığıyla eşleşen cihazına yazılır, yani kimse başkasının cihazına token bağlayamaz. Aynı token başka bir cihaz kaydında duruyorsa oradan düşürülür — aynı telefonda hesap değiştirildiğinde önceki kullanıcının bildirimleri yeni kullanıcıya gitmesin diye. Token değiştiğinde (uygulama yeniden kurulumu, yedekten geri yükleme) istemci bu ucu tekrar çağırmalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token kaydedildi"),
            @ApiResponse(responseCode = "400", description = "Token boş ya da çok uzun",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Bu oturuma ait cihaz kaydı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/devices/push-token")
    public ResponseEntity<Result> savePushToken(@RequestBody @Valid SavePushTokenDto request,
                                                HttpServletRequest httpRequest) {

        ClientContext client = ClientContext.from(httpRequest, clientIpResolver);
        deviceService.savePushToken(client.userAgent(), client.deviceId(), request.getPushToken());

        return ResponseEntity.ok(new SuccessResult(DeviceMessages.PUSH_TOKEN_SAVED, HttpStatus.OK));
    }

    @Operation(summary = "Cihazı adlandır",
            description = "Oturum listesinde ve detayında görünen cihaz adını kullanıcının seçtiği bir adla "
                    + "değiştirir (en fazla 60 karakter, ör. \"Evdeki iPad\"). Otomatik üretilen ad "
                    + "saklanmaya devam eder; yanıttaki customName kullanıcının verdiği ad, name ise "
                    + "gösterilen addır. Başkasının cihazı 404 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cihaz adlandırıldı"),
            @ApiResponse(responseCode = "400", description = "Ad boş ya da 60 karakterden uzun",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Cihaz bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PatchMapping("/devices/{deviceId}")
    public ResponseEntity<SuccessDataResult<DeviceDto>> renameDevice(@PathVariable UUID deviceId,
                                                                     @RequestBody @Valid RenameDeviceDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                deviceService.renameMyDevice(deviceId, request.getName()), DeviceMessages.RENAMED, HttpStatus.OK));
    }

    @Operation(summary = "Push bildirim token'ını sil",
            description = "Çıkış yaparken çağrılır; cihaz kaydı durur, yalnızca token düşürülür. Cihaz bulunamazsa sessizce geçer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token silindi")
    })
    @DeleteMapping("/devices/push-token")
    public ResponseEntity<Result> clearPushToken(HttpServletRequest httpRequest) {
        ClientContext client = ClientContext.from(httpRequest, clientIpResolver);
        deviceService.clearPushToken(client.userAgent(), client.deviceId());

        return ResponseEntity.ok(new SuccessResult(DeviceMessages.PUSH_TOKEN_CLEARED, HttpStatus.OK));
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
            @ApiResponse(responseCode = "400", description = "Dosya boş ya da desteklenmeyen türde",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "413", description = "Tek dosya 10 MiB ya da toplam multipart gövdesi 42 MiB sınırını aşıyor",
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
