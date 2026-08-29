package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.constants.CancellationMessages;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.constants.TimesheetMessages;
import com.exskylab.koala.core.dtos.cancellation.request.CancelAssignmentDto;
import com.exskylab.koala.core.dtos.cancellation.response.CancellationDto;
import com.exskylab.koala.core.dtos.cancellation.response.CancellationPreviewDto;
import com.exskylab.koala.core.dtos.dispute.request.OpenDisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import com.exskylab.koala.core.dtos.timesheet.response.WorkSessionDto;
import com.exskylab.koala.core.mappers.CancellationMapper;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.mappers.TimesheetMapper;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Atama işlemleri", description = "İptal, uyuşmazlık, puantaj ve iş tamamlama.")
@RestController
@RequestMapping("/job-assignments")
public class JobAssignmentOperationsController {

    private final CancellationService cancellationService;
    private final DisputeService disputeService;
    private final TimesheetService timesheetService;
    private final JobAssignmentService jobAssignmentService;
    private final JobAssignmentMapper jobAssignmentMapper;
    private final CancellationMapper cancellationMapper;
    private final DisputeMapper disputeMapper;
    private final TimesheetMapper timesheetMapper;

    public JobAssignmentOperationsController(CancellationService cancellationService,
                                             DisputeService disputeService,
                                             TimesheetService timesheetService,
                                             JobAssignmentService jobAssignmentService,
                                             JobAssignmentMapper jobAssignmentMapper,
                                             CancellationMapper cancellationMapper,
                                             DisputeMapper disputeMapper,
                                             TimesheetMapper timesheetMapper) {
        this.cancellationService = cancellationService;
        this.disputeService = disputeService;
        this.timesheetService = timesheetService;
        this.jobAssignmentService = jobAssignmentService;
        this.jobAssignmentMapper = jobAssignmentMapper;
        this.cancellationMapper = cancellationMapper;
        this.disputeMapper = disputeMapper;
        this.timesheetMapper = timesheetMapper;
    }

    @Operation(summary = "Atamayı iptal et",
            description = "İşçi iptal ederse işveren her koşulda tam iade alır. İşveren cezasız pencere içinde iptal ederse kesinti yoktur; pencere dışında geç iptal ücreti kesilir. İş başladıktan sonra iade yapılmaz. Atama başına tek iptal kaydı açılabilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İptal edildi"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Bu durumda iptal edilemez ya da zaten iptal edilmiş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/cancel")
    public ResponseEntity<SuccessDataResult<CancellationDto>> cancel(
            @PathVariable UUID assignmentId, @RequestBody @Valid CancelAssignmentDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                cancellationMapper.toCancellationDto(
                        cancellationService.cancelAssignment(assignmentId, request.getReason())),
                CancellationMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "İptal kesintisini önizle",
            description = "İptal etmeden, şu an iptal edilse ne kesileceğini döndürür. Hiçbir kayıt oluşturmaz. Kesinti iptal edenin tarafına göre değiştiği için hesap çağıranın kendi tarafına göre yapılır. Atama iptal edilebilir durumda değilse hata yerine cancellableNow=false döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Önizleme döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{assignmentId}/cancellation-preview")
    public ResponseEntity<SuccessDataResult<CancellationPreviewDto>> cancellationPreview(
            @PathVariable UUID assignmentId) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                cancellationService.previewCancellation(assignmentId),
                CancellationMessages.FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "İptal kaydını getir",
            description = "Uygulanan oran, kesilen ücret ve iade tutarını taşır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İptal kaydı döndürüldü"),
            @ApiResponse(responseCode = "404", description = "İptal kaydı yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{assignmentId}/cancellation")
    public ResponseEntity<SuccessDataResult<CancellationDto>> cancellation(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                cancellationService.getForAssignment(assignmentId)
                        .map(cancellationMapper::toCancellationDto).orElse(null),
                CancellationMessages.FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "Uyuşmazlık aç",
            description = "Yalnızca emanette para varken açılabilir. Atama başına aynı anda tek açık uyuşmazlık olur. Açık uyuşmazlık emanetteki parayı dondurur; işçiye çıkış yapılamaz ve otomatik tamamlama durur.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Uyuşmazlık açıldı"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Emanette para yok ya da zaten açık uyuşmazlık var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/disputes")
    public ResponseEntity<SuccessDataResult<DisputeDto>> openDispute(
            @PathVariable UUID assignmentId, @RequestBody @Valid OpenDisputeDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                disputeMapper.toDisputeDto(disputeService.openDispute(assignmentId, request.getReason())),
                DisputeMessages.OPENED, HttpStatus.CREATED));
    }

    @Operation(summary = "Atamanın uyuşmazlıkları")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uyuşmazlıklar listelendi"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{assignmentId}/disputes")
    public ResponseEntity<SuccessDataResult<List<DisputeDto>>> disputes(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeMapper.toDisputeDtoList(disputeService.getForAssignment(assignmentId)),
                DisputeMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İşe giriş yap",
            description = "Yalnızca işçinin kendisi yapabilir; işveren yalnızca okuyabilir. İlk giriş atamayı IN_PROGRESS durumuna taşır. Atama başına aynı anda tek açık oturum olur.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Giriş kaydedildi"),
            @ApiResponse(responseCode = "403", description = "Yalnızca işçi giriş yapabilir",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Zaten açık bir oturum var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/check-in")
    public ResponseEntity<SuccessDataResult<WorkSessionDto>> checkIn(
            @PathVariable UUID assignmentId, @RequestBody(required = false) @Valid CheckPointDto request) {

        CheckPointDto point = request == null ? new CheckPointDto() : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                timesheetMapper.toWorkSessionDto(
                        timesheetService.checkIn(assignmentId, point.getLatitude(), point.getLongitude())),
                TimesheetMessages.CHECKED_IN, HttpStatus.CREATED));
    }

    @Operation(summary = "İşten çıkış yap",
            description = "Oturum kapanınca çalışılan dakika yazılır ve atamanın workHours alanı tüm oturumların toplamından türetilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Çıkış kaydedildi"),
            @ApiResponse(responseCode = "403", description = "Yalnızca işçi çıkış yapabilir",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Açık oturum yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/check-out")
    public ResponseEntity<SuccessDataResult<WorkSessionDto>> checkOut(
            @PathVariable UUID assignmentId, @RequestBody(required = false) @Valid CheckPointDto request) {

        CheckPointDto point = request == null ? new CheckPointDto() : request;
        return ResponseEntity.ok(new SuccessDataResult<>(
                timesheetMapper.toWorkSessionDto(
                        timesheetService.checkOut(assignmentId, point.getLatitude(), point.getLongitude())),
                TimesheetMessages.CHECKED_OUT, HttpStatus.OK));
    }

    @Operation(summary = "Çalışma oturumları",
            description = "Giriş ve çıkış çiftleri, varsa konum bilgisiyle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturumlar listelendi"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{assignmentId}/work-sessions")
    public ResponseEntity<SuccessDataResult<List<WorkSessionDto>>> sessions(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                timesheetMapper.toWorkSessionDtoList(timesheetService.getSessions(assignmentId)),
                TimesheetMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İşi tamamlanmış işaretle",
            description = "Yalnızca ilan sahibi ya da şirket yetkilisi tamamlayabilir. Yalnızca CONFIRMED ve IN_PROGRESS durumlarından geçilebilir. Tamamlanınca işçinin rozetleri yeniden değerlendirilir ve emanetteki para çıkışa hazır hâle gelir. İşveren hiç onaylamazsa iş bitiminden belirli gün sonra sistem kendiliğinden tamamlar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İş tamamlandı"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Atama bu durumdan tamamlanamaz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/complete")
    public ResponseEntity<SuccessDataResult<MyAssignmentDto>> complete(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAssignmentMapper.toMyAssignmentDto(jobAssignmentService.completeAssignment(assignmentId)),
                JobAssignmentMessages.COMPLETED, HttpStatus.OK));
    }
}
