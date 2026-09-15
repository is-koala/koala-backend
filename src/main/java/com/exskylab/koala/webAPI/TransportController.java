package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.core.constants.TransportMessages;
import com.exskylab.koala.core.dtos.transport.request.SeatSelectionDto;
import com.exskylab.koala.core.dtos.transport.request.TransportPlanWriteDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.dtos.transport.response.TransportPlanDto;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
