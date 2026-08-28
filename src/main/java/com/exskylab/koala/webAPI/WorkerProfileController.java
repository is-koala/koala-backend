package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.AvailabilityService;
import com.exskylab.koala.business.abstracts.ExperienceService;
import com.exskylab.koala.business.abstracts.JobAlertService;
import com.exskylab.koala.core.constants.AvailabilityMessages;
import com.exskylab.koala.core.constants.ExperienceMessages;
import com.exskylab.koala.core.constants.JobAlertMessages;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityDto;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityExceptionDto;
import com.exskylab.koala.core.dtos.availability.response.AvailabilityDto;
import com.exskylab.koala.core.dtos.availability.response.AvailabilityExceptionDto;
import com.exskylab.koala.core.dtos.experience.request.SaveExperienceDto;
import com.exskylab.koala.core.dtos.experience.response.ExperienceDto;
import com.exskylab.koala.core.dtos.jobAlert.request.SaveJobAlertDto;
import com.exskylab.koala.core.dtos.jobAlert.response.JobAlertDto;
import com.exskylab.koala.core.mappers.AvailabilityMapper;
import com.exskylab.koala.core.mappers.ExperienceMapper;
import com.exskylab.koala.core.mappers.JobAlertMapper;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "İşçi araçları", description = "Deneyim, müsaitlik ve iş alarmları.")
@RestController
public class WorkerProfileController {

    private final ExperienceService experienceService;
    private final AvailabilityService availabilityService;
    private final JobAlertService jobAlertService;
    private final ExperienceMapper experienceMapper;
    private final AvailabilityMapper availabilityMapper;
    private final JobAlertMapper jobAlertMapper;

    public WorkerProfileController(ExperienceService experienceService,
                                   AvailabilityService availabilityService,
                                   JobAlertService jobAlertService,
                                   ExperienceMapper experienceMapper,
                                   AvailabilityMapper availabilityMapper,
                                   JobAlertMapper jobAlertMapper) {
        this.experienceService = experienceService;
        this.availabilityService = availabilityService;
        this.jobAlertService = jobAlertService;
        this.experienceMapper = experienceMapper;
        this.availabilityMapper = availabilityMapper;
        this.jobAlertMapper = jobAlertMapper;
    }

    @Operation(summary = "Deneyimlerim")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deneyimler listelendi")
    })
    @GetMapping("/users/me/experiences")
    public ResponseEntity<SuccessDataResult<List<ExperienceDto>>> myExperiences() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                experienceMapper.toExperienceDtoList(experienceService.getMyExperiences()),
                ExperienceMessages.LISTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Kullanıcının deneyimleri",
            description = "Herkese açıktır; işveren başvuran işçinin deneyimini görebilmelidir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deneyimler listelendi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/users/{userId}/experiences")
    public ResponseEntity<SuccessDataResult<List<ExperienceDto>>> experiencesOf(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                experienceMapper.toExperienceDtoList(experienceService.getExperiencesOf(userId)),
                ExperienceMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Deneyim ekle",
            description = "Bitiş tarihi boş bırakılırsa deneyim hâlâ sürüyor demektir. Başlangıç gelecekte olamaz, bitiş başlangıçtan önce olamaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Deneyim eklendi"),
            @ApiResponse(responseCode = "400", description = "Tarih tutarsız ya da alan doğrulaması başarısız",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/users/me/experiences")
    public ResponseEntity<SuccessDataResult<ExperienceDto>> addExperience(
            @RequestBody @Valid SaveExperienceDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                experienceMapper.toExperienceDto(experienceService.addExperience(request)),
                ExperienceMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Deneyimi güncelle",
            description = "Başkasının kaydına dokunmak 404 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deneyim güncellendi"),
            @ApiResponse(responseCode = "400", description = "Tarih tutarsız",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kayıt bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/users/me/experiences/{experienceId}")
    public ResponseEntity<SuccessDataResult<ExperienceDto>> updateExperience(
            @PathVariable UUID experienceId, @RequestBody @Valid SaveExperienceDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                experienceMapper.toExperienceDto(experienceService.updateExperience(experienceId, request)),
                ExperienceMessages.UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Deneyimi sil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deneyim silindi"),
            @ApiResponse(responseCode = "404", description = "Kayıt bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/users/me/experiences/{experienceId}")
    public ResponseEntity<Result> deleteExperience(@PathVariable UUID experienceId) {
        experienceService.deleteExperience(experienceId);
        return ResponseEntity.ok(new SuccessResult(ExperienceMessages.DELETED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Kullanıcının müsaitliği",
            description = "Haftalık şablon ve tarih istisnalarını birlikte döner. Herkese açıktır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Müsaitlik döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/users/{userId}/availability")
    public ResponseEntity<SuccessDataResult<AvailabilityDto>> availabilityOf(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                new AvailabilityDto(
                        availabilityMapper.toSlotDtoList(availabilityService.getSlotsOf(userId)),
                        availabilityMapper.toExceptionDtoList(availabilityService.getUpcomingExceptionsOf(userId))),
                AvailabilityMessages.FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "Haftalık müsaitliği toptan değiştir",
            description = "Mevcut tüm aralıklar silinip yenileri yazılır; istemci takvimi bir bütün olarak gönderir. Aynı gün içinde çakışan aralık reddedilir, farklı günlerde aynı saatler çakışma sayılmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Müsaitlik güncellendi"),
            @ApiResponse(responseCode = "400", description = "Aralık bitişi başlangıçtan önce",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Aynı gün içinde çakışan aralık",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/users/me/availability")
    public ResponseEntity<SuccessDataResult<AvailabilityDto>> replaceAvailability(
            @RequestBody @Valid SaveAvailabilityDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                new AvailabilityDto(
                        availabilityMapper.toSlotDtoList(availabilityService.replaceMySlots(request)),
                        List.of()),
                AvailabilityMessages.SAVED, HttpStatus.OK));
    }

    @Operation(summary = "Tarih istisnası kaydet",
            description = "Tarih başına tekildir; aynı gün ikinci kez kaydedilirse mevcut satır güncellenir. Geçmiş tarihe istisna eklenemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İstisna kaydedildi"),
            @ApiResponse(responseCode = "400", description = "Geçmiş tarihe istisna eklenemez",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/users/me/availability/exceptions")
    public ResponseEntity<SuccessDataResult<AvailabilityExceptionDto>> saveAvailabilityException(
            @RequestBody @Valid SaveAvailabilityExceptionDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                availabilityMapper.toExceptionDto(availabilityService.saveMyException(request)),
                AvailabilityMessages.EXCEPTION_SAVED, HttpStatus.OK));
    }

    @Operation(summary = "Tarih istisnasını sil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İstisna silindi"),
            @ApiResponse(responseCode = "404", description = "Kayıt bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/users/me/availability/exceptions/{exceptionId}")
    public ResponseEntity<Result> deleteAvailabilityException(@PathVariable UUID exceptionId) {
        availabilityService.deleteMyException(exceptionId);
        return ResponseEntity.ok(new SuccessResult(AvailabilityMessages.EXCEPTION_DELETED, HttpStatus.OK));
    }

    @Operation(summary = "İş alarmlarım")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alarmlar listelendi")
    })
    @GetMapping("/users/me/job-alerts")
    public ResponseEntity<SuccessDataResult<List<JobAlertDto>>> myJobAlerts() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAlertMapper.toJobAlertDtoList(jobAlertService.getMyAlerts()),
                JobAlertMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İş alarmı oluştur",
            description = "Kategori, ilan türü, asgari ücret, il ve ilçe kriterlerinden en az biri zorunludur; yoksa alarm her ilanda ateşlenirdi. Kullanıcı başına alarm sayısı sınırlıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alarm oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "En az bir kriter gerekli",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Alarm sayısı sınırına ulaşıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/users/me/job-alerts")
    public ResponseEntity<SuccessDataResult<JobAlertDto>> createJobAlert(
            @RequestBody @Valid SaveJobAlertDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                jobAlertMapper.toJobAlertDto(jobAlertService.createAlert(request)),
                JobAlertMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "İş alarmını güncelle")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alarm güncellendi"),
            @ApiResponse(responseCode = "400", description = "En az bir kriter gerekli",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Alarm bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/users/me/job-alerts/{alertId}")
    public ResponseEntity<SuccessDataResult<JobAlertDto>> updateJobAlert(
            @PathVariable UUID alertId, @RequestBody @Valid SaveJobAlertDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAlertMapper.toJobAlertDto(jobAlertService.updateAlert(alertId, request)),
                JobAlertMessages.UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "İş alarmını sil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alarm silindi"),
            @ApiResponse(responseCode = "404", description = "Alarm bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/users/me/job-alerts/{alertId}")
    public ResponseEntity<Result> deleteJobAlert(@PathVariable UUID alertId) {
        jobAlertService.deleteAlert(alertId);
        return ResponseEntity.ok(new SuccessResult(JobAlertMessages.DELETED, HttpStatus.OK));
    }
}
