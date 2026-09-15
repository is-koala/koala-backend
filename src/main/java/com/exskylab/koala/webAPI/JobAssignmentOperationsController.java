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
import com.exskylab.koala.business.abstracts.IdentityRevealService;
import com.exskylab.koala.business.abstracts.TimesheetService;
import com.exskylab.koala.core.constants.CancellationMessages;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.constants.IdentityRevealMessages;
import com.exskylab.koala.core.constants.TimesheetMessages;
import com.exskylab.koala.core.dtos.cancellation.request.CancelAssignmentDto;
import com.exskylab.koala.core.dtos.cancellation.response.CancellationDto;
import com.exskylab.koala.core.dtos.cancellation.response.CancellationPreviewDto;
import com.exskylab.koala.core.dtos.dispute.request.OpenDisputeDto;
import com.exskylab.koala.core.dtos.dispute.response.DisputeDto;
import com.exskylab.koala.core.dtos.identity.response.WorkerIdentityDto;
import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import com.exskylab.koala.core.dtos.timesheet.response.WorkSessionDto;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.mappers.CancellationMapper;
import com.exskylab.koala.core.mappers.DisputeMapper;
import com.exskylab.koala.core.mappers.IdentityRevealMapper;
import com.exskylab.koala.core.mappers.TimesheetMapper;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.servlet.http.HttpServletRequest;
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
    private final CancellationMapper cancellationMapper;
    private final DisputeMapper disputeMapper;
    private final TimesheetMapper timesheetMapper;
    private final IdentityRevealService identityRevealService;
    private final IdentityRevealMapper identityRevealMapper;
    private final ClientIpResolver clientIpResolver;

    public JobAssignmentOperationsController(CancellationService cancellationService,
                                             DisputeService disputeService,
                                             TimesheetService timesheetService,
                                             JobAssignmentService jobAssignmentService,
                                             CancellationMapper cancellationMapper,
                                             DisputeMapper disputeMapper,
                                             TimesheetMapper timesheetMapper,
                                             IdentityRevealService identityRevealService,
                                             IdentityRevealMapper identityRevealMapper,
                                             ClientIpResolver clientIpResolver) {
        this.cancellationService = cancellationService;
        this.disputeService = disputeService;
        this.timesheetService = timesheetService;
        this.jobAssignmentService = jobAssignmentService;
        this.cancellationMapper = cancellationMapper;
        this.disputeMapper = disputeMapper;
        this.timesheetMapper = timesheetMapper;
        this.identityRevealService = identityRevealService;
        this.identityRevealMapper = identityRevealMapper;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Atamayı iptal et",
            description = "İşçi iptal ederse işveren her koşulda tam iade alır. İşveren cezasız pencere içinde iptal ederse kesinti yoktur; pencere dışında geç iptal ücreti kesilir. İş başladıktan sonra iade yapılmaz. Atama başına tek iptal kaydı açılabilir. Para alanları exact decimal string, iade durumu yalnız bu iptalin doğrudan oluşturduğu iadedir.")
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

        var cancellation = cancellationService.cancelAssignment(assignmentId, request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                cancellationMapper.toCancellationDto(cancellation),
                CancellationMessages.CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "İptal kesintisini önizle",
            description = "İptal etmeden, şu an iptal edilse ne kesileceğini döndürür. Hiçbir kayıt oluşturmaz. Kesinti iptal edenin tarafına göre değiştiği için hesap çağıranın kendi tarafına göre yapılır. Atama iptal edilebilir durumda değilse hata yerine cancellableNow=false döner. Para alanları bilimsel gösterim kullanmayan exact decimal string'dir.")
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
            description = "Uygulanan oran, kesilen ücret ve iade tutarını exact decimal string olarak taşır. İade yaşam döngüsü yalnız iptale doğrudan bağlı iadedir; eski kayıtlarda bağlantı yoksa null olabilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İptal kaydı döndürüldü"),
            @ApiResponse(responseCode = "404", description = "İptal kaydı yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{assignmentId}/cancellation")
    public ResponseEntity<SuccessDataResult<CancellationDto>> cancellation(@PathVariable UUID assignmentId) {
        var cancellation = cancellationService.getForAssignment(assignmentId).orElse(null);
        return ResponseEntity.ok(new SuccessDataResult<>(
                cancellationMapper.toCancellationDto(cancellation),
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
            description = "Yalnızca işçinin kendisi yapabilir; işveren yalnızca okuyabilir. İlk giriş atamayı IN_PROGRESS durumuna taşır. Atama başına aynı anda tek açık oturum olur. clientEventId ve capturedAt birlikte gönderildiğinde aynı normalize payload güvenle tekrar edilebilir; eski gövde geriye uyumludur.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Giriş kaydedildi"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da işçisi değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Zaten açık bir oturum var ya da olay kimliği farklı payload ile kullanıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/check-in")
    public ResponseEntity<SuccessDataResult<WorkSessionDto>> checkIn(
            @PathVariable UUID assignmentId, @RequestBody(required = false) @Valid CheckPointDto request) {

        CheckPointDto point = request == null ? new CheckPointDto() : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                timesheetMapper.toWorkSessionDto(
                        timesheetService.checkIn(assignmentId, point)),
                TimesheetMessages.CHECKED_IN, HttpStatus.CREATED));
    }

    @Operation(summary = "İşten çıkış yap",
            description = "Oturum kapanınca çalışılan dakika etkili olay zamanlarından hesaplanır ve atamanın workHours alanı tüm oturumların toplamından türetilir. clientEventId ile kapanmış bir oturum güvenle tekrar okunabilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Çıkış kaydedildi"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da işçisi değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Açık oturum yok ya da olay kimliği farklı payload ile kullanıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/check-out")
    public ResponseEntity<SuccessDataResult<WorkSessionDto>> checkOut(
            @PathVariable UUID assignmentId, @RequestBody(required = false) @Valid CheckPointDto request) {

        CheckPointDto point = request == null ? new CheckPointDto() : request;
        return ResponseEntity.ok(new SuccessDataResult<>(
                timesheetMapper.toWorkSessionDto(
                        timesheetService.checkOut(assignmentId, point)),
                TimesheetMessages.CHECKED_OUT, HttpStatus.OK));
    }

    @Operation(summary = "Çalışma oturumları",
            description = "Giriş ve çıkış çiftleri; varsa konum, ham cihaz zamanı, ilk alım zamanı ve plausibility bayraklarıyla.")
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

    @Operation(summary = "İşçinin kimlik bilgileri (sigorta bildirimi için)",
            description = "İşe alınmış işçinin SGK işe giriş bildirimi için gereken kimlik bilgilerini döner: kimlikten "
                    + "gelen ad soyad, TC kimlik numarası, doğum tarihi, doğrulama zamanı ve yöntemi (CHIP: TCKK çipi, "
                    + "DOCUMENT: personel onaylı TCKK fotoğrafları; eski kayıtlarda ikisi de null olabilir). Yalnız ilan "
                    + "sahibi ya da şirket yetkilisi okuyabilir ve yalnız işçinin kabul ettiği atamada "
                    + "(CONFIRMED, IN_PROGRESS, COMPLETED); başvuru aşamasındaki (PENDING_CONFIRMATION) atama, işçi ve "
                    + "yabancı için yanıt var olmayan atamayla aynıdır. Her çağrı kim/hangi atama/ne zaman/maskeli IP "
                    + "ile kayda geçer ve işçiye hesap güvenliği kategorisinde in-app + push bildirim gider; aynı "
                    + "işveren aynı atamada 24 saat içinde tekrar okursa kayıt yazılır, bildirim tekrarlanmaz. TC kimlik "
                    + "numarası başka hiçbir yanıtta yer almaz. İşveren başına saatte en çok 30 okuma.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kimlik bilgileri döndü, görüntüleme kayda geçti"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı, işvereni değilsiniz ya da işçi henüz işe alınmadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "İşçinin kimliği doğrulanmamış (WORKER_IDENTITY_NOT_VERIFIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Saatlik kimlik görüntüleme sınırı aşıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{assignmentId}/worker-identity")
    public ResponseEntity<SuccessDataResult<WorkerIdentityDto>> workerIdentity(@PathVariable UUID assignmentId,
                                                                               HttpServletRequest request) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                identityRevealMapper.toWorkerIdentityDto(
                        identityRevealService.revealWorkerIdentity(assignmentId, clientIpResolver.resolve(request))),
                IdentityRevealMessages.REVEALED, HttpStatus.OK));
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
                jobAssignmentService.completeAssignment(assignmentId),
                JobAssignmentMessages.COMPLETED, HttpStatus.OK));
    }
}
