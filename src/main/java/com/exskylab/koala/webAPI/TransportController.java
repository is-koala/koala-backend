package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.core.dtos.transport.request.SeatBoardingDto;
import com.exskylab.koala.core.dtos.transport.request.SeatSelectionDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleBoardingDto;
import com.exskylab.koala.core.dtos.transport.request.TransportPlanWriteDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingEventDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingQrDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleBoardingRowDto;
import com.exskylab.koala.core.dtos.transport.response.TransportPlanDto;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.TransportDirection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Ulaşım planı", description = "İlanın servis/durak planı, işçinin koltuk seçimi ve ulaşım görünümü.")
@RestController
@RequestMapping
public class TransportController {

    private final TransportService transportService;

    public TransportController(TransportService transportService) {
        this.transportService = transportService;
    }

    @Operation(summary = "İlanın ulaşım planını yaz",
            description = "Planı bütünüyle yazar: gidiş/dönüş yöntemleri, yol parası, servisler ve duraklar. "
                    + "Yalnız ilan sahibi ya da ilanın şirketinin yetkilisi; yetkisiz kullanıcı 404 alır ve ilanın varlığı sızmaz. "
                    + "İlan yayınlandıktan sonra da düzenlenebilir. Listede id taşıyan servis/durak yerinde güncellenir ve "
                    + "koltuk seçimleri korunur; id'siz olanlar yeni açılır; listede yer almayan mevcut servis ve duraklar "
                    + "koltuklarıyla birlikte kaldırılır. Durak adresi mevcut adres sözleşmesiyle (AddressWriteV2) verilir. "
                    + "Kayıttan sonra ilana atanmış (CONFIRMED/IN_PROGRESS) işçilere TRANSPORT kategorisinde bildirim gider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan kaydedildi; işveren görünümü döndürüldü"),
            @ApiResponse(responseCode = "400", description = "Doğrulama hatası: yöntemsiz plan, SHUTTLE olmayan yönde servis, "
                    + "CASH_ALLOWANCE'sız tutar, duraksız servis, adressiz yeni durak, hizmet alanı dışı adres",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı ya da yetki yok; listede verilen servis/durak bu plana ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Servis kapasitesi seçilmiş koltuk sayısının altına çekilemez",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/jobs/{jobId}/transport-plan")
    public ResponseEntity<SuccessDataResult<TransportPlanDto>> writePlan(@PathVariable UUID jobId,
                                                                          @RequestBody @Valid TransportPlanWriteDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.writePlan(jobId, request),
                TransportMessages.PLAN_SAVED, HttpStatus.OK));
    }

    @Operation(summary = "İlanın ulaşım planını getir (işveren görünümü)",
            description = "Plaka, şoför bilgisi, tam durak adresleri ve servis/durak başına seçilmiş koltuk sayısı dahil. "
                    + "Yalnız ilan sahibi ya da ilanın şirketinin yetkilisi; yetkisiz kullanıcı 404 alır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan döndürüldü"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı, yetki yok ya da ilanın planı yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/jobs/{jobId}/transport-plan")
    public ResponseEntity<SuccessDataResult<TransportPlanDto>> getPlan(@PathVariable UUID jobId) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.getPlan(jobId),
                TransportMessages.PLAN_RETRIEVED, HttpStatus.OK));
    }

    @Operation(summary = "Atamanın ulaşım görünümü",
            description = "İşçi görünümünde ilanın servisleri ve durakları tam adresle, kendi koltukları ve yalnız koltuk seçtiği "
                    + "servisin plaka/şoför bilgisi döner; işveren görünümünde her servisin plaka/şoför bilgisi döner. "
                    + "Taraf olmayan kullanıcı 404 alır ve atamanın varlığı sızmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ulaşım bilgisi döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı, taraf değilsiniz ya da ilanın planı yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/job-assignments/{assignmentId}/transport")
    public ResponseEntity<SuccessDataResult<AssignmentTransportDto>> getAssignmentTransport(
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.getAssignmentTransport(assignmentId),
                TransportMessages.ASSIGNMENT_TRANSPORT_RETRIEVED, HttpStatus.OK));
    }

    @Operation(summary = "Koltuk seç",
            description = "Atama için verilen servis ve durağı seçer; yön servisin yönünden türer ve o yöndeki önceki seçimin "
                    + "yerine geçer (yön başına tek koltuk). İşçi kendisi seçer; işveren de aynı ucu işçi adına çağırabilir. "
                    + "Yalnız PENDING_CONFIRMATION/CONFIRMED/IN_PROGRESS atamada; başka ilanın servisi 404 ile gizlenir; "
                    + "dolu servis 409 TRANSPORT_SHUTTLE_FULL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Koltuk kaydedildi; güncel ulaşım görünümü döndürüldü"),
            @ApiResponse(responseCode = "400", description = "shuttleId ya da stopId eksik",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı, taraf değilsiniz, ilanın planı yok, servis ya da durak bu plana ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Servis dolu ya da atama koltuk seçimine kapalı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PutMapping("/job-assignments/{assignmentId}/transport/seat")
    public ResponseEntity<SuccessDataResult<AssignmentTransportDto>> selectSeat(
            @PathVariable UUID assignmentId, @RequestBody @Valid SeatSelectionDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.selectSeat(assignmentId, request),
                TransportMessages.SEAT_SAVED, HttpStatus.OK));
    }

    @Operation(summary = "Servis QR kodu",
            description = "İlan sahibi ya da şirket yetkilisi servisin HMAC biniş QR'ını alır; uri "
                    + "{şema}://board?t=<token> biçimindedir (şema ortama göre koala-dev/koala-staging/koala; token s1. "
                    + "ile başlar ve servisi kendisi taşır). Varsayılan QR dönen pencerelidir: "
                    + "token expiresAt'e kadar kabul edilir, ekran refreshAt'te yenilenmelidir. static=true kâğıda basılabilen "
                    + "süresiz QR üretir; bu QR ile yapılan biniş STATIC_QR bayrağı taşır ve ancak QR anahtarı yenilenince "
                    + "geçersizleşir. Yetkisiz kullanıcı 404 alır ve servisin varlığı sızmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR token döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Servis bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/shuttles/{shuttleId}/qr")
    public ResponseEntity<SuccessDataResult<BoardingQrDto>> issueShuttleQr(
            @PathVariable UUID shuttleId,
            @Parameter(description = "true ise süresiz, basılabilir sabit QR")
            @RequestParam(name = "static", defaultValue = "false") boolean staticQr) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.issueShuttleQr(shuttleId, staticQr),
                TransportMessages.QR_ISSUED, HttpStatus.OK));
    }

    @Operation(summary = "Servis QR anahtarını yenile",
            description = "Servisin QR imza anahtarını yeniler: basılmış sabit QR dahil önceki bütün servis QR'ları ve "
                    + "o servisteki işçi QR'ları anında geçersiz olur (okutma 404). Yeni dönen pencereli QR döner; basılı "
                    + "sürüm gerekiyorsa ardından GET ?static=true çağrılır. Yalnız ilan sahibi ya da şirket yetkilisi; "
                    + "yetkisiz kullanıcı 404 alır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Anahtar yenilendi; yeni QR döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Servis bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/shuttles/{shuttleId}/qr/rotate")
    public ResponseEntity<SuccessDataResult<BoardingQrDto>> rotateShuttleQr(@PathVariable UUID shuttleId) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.rotateShuttleQr(shuttleId),
                TransportMessages.QR_ROTATED, HttpStatus.OK));
    }

    @Operation(summary = "İşçi biniş QR kodu",
            description = "İşçinin verilen yöndeki koltuğu için HMAC QR'ı; uri {şema}://board?t=<token> biçimindedir "
                    + "(token w1. ile başlar ve koltuğu taşır), işveren okutur. direction verilmezse OUTBOUND. Yalnız "
                    + "atamanın işçisi; işveren ya da taraf olmayan 404 alır. Atama CONFIRMED/IN_PROGRESS/COMPLETED "
                    + "değilse ya da o yönde koltuk yoksa 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR token döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da işçisi değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Bu yönde koltuk seçilmemiş ya da atama binişe kapalı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/job-assignments/{assignmentId}/transport/qr")
    public ResponseEntity<SuccessDataResult<BoardingQrDto>> issueWorkerQr(
            @PathVariable UUID assignmentId,
            @Parameter(description = "Koltuğun yönü; varsayılan OUTBOUND")
            @RequestParam(name = "direction", required = false) TransportDirection direction) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.issueWorkerQr(assignmentId, direction),
                TransportMessages.QR_ISSUED, HttpStatus.OK));
    }

    @Operation(summary = "Servis QR ile biniş",
            description = "İşçi servis QR'ını okutur; kendi koltuğuna BOARDED yazılır (kaynak WORKER_SCANNED_SHUTTLE_QR). "
                    + "İlanın tarafı olmayan, bozuk ya da anahtarı yenilenmiş token 404 ve olmayan servisle aynı zarf. İlanın "
                    + "işçisi başka servisin QR'ını okutursa 409 TRANSPORT_WRONG_SHUTTLE, koltuk seçmemişse 409 "
                    + "TRANSPORT_SEAT_NOT_SELECTED. Token sunucu saatiyle geçerli olmalı; değilse okutulduğu an (capturedAt) "
                    + "token'ın kabul aralığındaysa (mint penceresinin başından expiresAt'e) ve gönderim late-sync süresi "
                    + "içindeyse LATE_SYNC bayrağıyla kabul edilir, "
                    + "aksi 400 TRANSPORT_TOKEN_EXPIRED. İdempotent: aynı koltukta aynı clientEventId ya da o kalkış için "
                    + "kayıtlı BOARDED, token'a bakılmadan kayıtlı olayı replayed=true ile 200 döndürür; aynı clientEventId "
                    + "farklı gövdeyle 409. Konum ve saat yalnız bayrak üretir, binişi reddetmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Biniş kaydedildi ya da kayıtlı biniş döndürüldü (replayed)"),
            @ApiResponse(responseCode = "400", description = "Token süresi doldu ya da doğrulama hatası",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Servis bulunamadı, ilanın tarafı değilsiniz ya da token bu servise ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Yanlış servis, koltuk seçilmemiş, atama binişe kapalı, "
                    + "clientEventId çakışması ya da eşzamanlı yazım",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/shuttles/{shuttleId}/boardings")
    public ResponseEntity<SuccessDataResult<BoardingEventDto>> boardOnShuttle(
            @PathVariable UUID shuttleId, @RequestBody @Valid ShuttleBoardingDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.boardOnShuttle(shuttleId, request),
                TransportMessages.BOARDING_RECORDED, HttpStatus.OK));
    }

    @Operation(summary = "Koltuk binişi (işveren)",
            description = "İşveren işçinin QR'ını okutur (token; kaynak EMPLOYER_SCANNED_WORKER_QR) ya da listeden BOARDED/NO_SHOW "
                    + "işaretler (token yok; kaynak EMPLOYER_MARKED). Yalnız ilan sahibi ya da şirket yetkilisi; yetkisiz, "
                    + "başka koltuğun ya da yenilenmiş anahtarın token'ı 404. Token kuralları servis QR'ıyla aynı (LATE_SYNC, "
                    + "400 TRANSPORT_TOKEN_EXPIRED). İdempotent: o kalkış zaten BOARDED ise ya da aynı clientEventId kayıtlıysa "
                    + "token'a bakılmadan kayıtlı olay replayed=true ile 200; tekrar NO_SHOW kayıtlı işareti döner; binmiş "
                    + "koltuğa NO_SHOW 409 TRANSPORT_ALREADY_BOARDED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Biniş kaydedildi ya da kayıtlı olay döndürüldü (replayed)"),
            @ApiResponse(responseCode = "400", description = "Token süresi doldu ya da doğrulama hatası",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Koltuk bulunamadı, yetki yok ya da token bu koltuğa ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Binmiş koltuğa NO_SHOW, atama binişe kapalı, clientEventId "
                    + "çakışması ya da eşzamanlı yazım",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/seat-assignments/{seatAssignmentId}/boardings")
    public ResponseEntity<SuccessDataResult<BoardingEventDto>> markSeatBoarding(
            @PathVariable UUID seatAssignmentId, @RequestBody @Valid SeatBoardingDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.markSeatBoarding(seatAssignmentId, request),
                TransportMessages.BOARDING_RECORDED, HttpStatus.OK));
    }

    @Operation(summary = "Servis biniş listesi",
            description = "İlan sahibi ya da şirket yetkilisi; servisteki her canlı koltuk (CONFIRMED/IN_PROGRESS/COMPLETED "
                    + "atama) için durağın kalkış günündeki durum: boarded ve durumu belirleyen olay (BOARDED, yoksa son "
                    + "NO_SHOW, yoksa null) bayraklarıyla. İptal edilmiş atamalar listede yok. Yetkisiz 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Biniş listesi döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Servis bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/shuttles/{shuttleId}/boardings")
    public ResponseEntity<SuccessDataResult<List<ShuttleBoardingRowDto>>> listBoardings(@PathVariable UUID shuttleId) {
        return ResponseEntity.ok(new SuccessDataResult<>(transportService.listBoardings(shuttleId),
                TransportMessages.BOARDINGS_RETRIEVED, HttpStatus.OK));
    }
}
