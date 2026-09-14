package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.StaffPayoutService;
import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.constants.PayoutMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.dtos.payout.request.RefundRequestDto;
import com.exskylab.koala.core.dtos.payout.request.ReleaseRequestDto;
import com.exskylab.koala.core.dtos.payout.response.AdminPayoutDetailDto;
import com.exskylab.koala.core.dtos.payout.response.PayoutDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.helpers.CreatedAtRange;
import com.exskylab.koala.core.mappers.PayoutMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.PayoutReleaseOrigin;
import com.exskylab.koala.entities.PayoutStatus;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Ödeme çıkışları", description = "İşçiye çıkış, iade ve kendi ödemelerim.")
@RestController
@RequestMapping
public class PayoutsController {

    private final PayoutService payoutService;
    private final StaffPayoutService staffPayoutService;
    private final PayoutMapper payoutMapper;
    private final SecurityService securityService;

    public PayoutsController(PayoutService payoutService, StaffPayoutService staffPayoutService,
                             PayoutMapper payoutMapper, SecurityService securityService) {
        this.payoutService = payoutService;
        this.staffPayoutService = staffPayoutService;
        this.payoutMapper = payoutMapper;
        this.securityService = securityService;
    }

    @Operation(summary = "Bana yapılan ödemeler",
            description = "Oturum sahibinin hakedişleri, en yeni önce. Her satır hakedişin gittiği hesabın maskeli "
                    + "IBAN'ını (ilk 4 + *** + son 4; tam IBAN dönmez) ve bankAccountId'sini, sağlayıcının onay "
                    + "referansını (providerReference; PENDING'de null), çıkışa alınma anını (releasedAt) ve banka "
                    + "ekstresiyle doğrulanan gerçekleşme anını (settledAt; yalnız COMPLETED'da dolu) taşır. "
                    + "Durum sözlüğü: PENDING = çıkışa alındı, sağlayıcı onayı bekleniyor; PROCESSING = sağlayıcı "
                    + "onayladı, banka transferi yolda; COMPLETED = banka ekstresiyle doğrulandı; FAILED = çıkış "
                    + "başarısız (failureReason açıklar). Hesap kaydı yok ya da hesap sonradan silinmişse maskedIban ve "
                    + "bankAccountId null döner; uydurulmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ödemeler listelendi")
    })
    @GetMapping("/users/me/payouts")
    public ResponseEntity<SuccessDataResult<List<PayoutDto>>> myPayouts() {
        List<PayoutDto> payouts = payoutMapper.toPayoutDtoList(
                payoutService.getPayoutsForWorker(securityService.getAuthenticatedUserId()));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                payouts, PayoutMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Emanetteki parayı işçiye çıkar",
            description = "Yalnızca atama COMPLETED ise çalışır ve açık uyuşmazlık varsa reddedilir. Aynı atama için ikinci kez çağrılırsa yeni kayıt açılmaz. Sağlayıcı mutabakatı senkron yapılmaz; kuyruğa alınıp üstel geri çekilmeyle denenir. Gövde isteğe bağlıdır; verilen gerekçe denetim kaydına düşer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Çıkış kaydı oluşturuldu"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Atama tamamlanmamış, para emanette değil ya da açık uyuşmazlık var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.PAYOUT_RELEASE, action = "payout.release")
    @PostMapping("/admin/job-assignments/{assignmentId}/release")
    public ResponseEntity<SuccessDataResult<PayoutDto>> release(@PathVariable UUID assignmentId,
                                                                @RequestBody(required = false) @Valid ReleaseRequestDto request,
                                                                @AuthenticationPrincipal StaffPrincipal actor) {
        String reason = request == null ? null : request.getReason();
        PayoutDto payout = payoutMapper.toPayoutDto(
                staffPayoutService.releaseToWorker(actor, assignmentId, reason, PayoutReleaseOrigin.STAFF));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                payout, PayoutMessages.RELEASED, HttpStatus.OK));
    }

    @Operation(summary = "Hakediş kuyruğu",
            description = "status verilmezse yalnız PROCESSING (banka mutabakatı bekleyen) döner. "
                    + "Her satır ekstre eşleştirmesi için kayıtlı sağlayıcı referansını, alıcı özetini ve "
                    + "maskeli IBAN'ı (ilk 4 + son 4) taşır; tam IBAN dönmez. Tutar ondalık metindir. "
                    + "İşçi hesabını silmişse satır kuyrukta kalır, recipient ve maskedIban null döner. "
                    + "Onay mevcut reconcile ucundan yapılır; bu GET yalnız okur. "
                    + "query (2-100 karakter, kırpılır; kısa ya da boş değer süzmez) hakediş id'si, atama id'si ve alıcının "
                    + "ad-soyadı ile e-postasında büyük-küçük harf ve Türkçe karakter duyarsız arar; status süzgeciyle "
                    + "kesişir; 100 karakteri aşarsa 400. workerId verilirse yalnız o işçinin hakedişleri döner. "
                    + "from ve to hakedişin oluşturulma anını (createdAt) süzer, ikisi de dahildir ve dilimli ISO-8601 "
                    + "an ister; yalnız biri verilebilir; status, query ve workerId ile kesişir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hakedişler listelendi"),
            @ApiResponse(responseCode = "400", description = "query 100 karakteri aşıyor ya da tarih dilimsiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.PAYOUT_RECONCILE, action = "payout.reconcile")
    @GetMapping("/admin/payouts")
    public ResponseEntity<SuccessDataResult<PageDto<StaffPayoutDto>>> list(
            @RequestParam(required = false) List<PayoutStatus> status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID workerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal StaffPrincipal actor) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                staffPayoutService.listForStaff(actor, status, query, workerId, CreatedAtRange.of(from, to), pageable),
                PayoutMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Hakediş detayı",
            description = "Tek hakediş: bağlı atama ve ilan, işçi ve işveren (id, ad, e-posta), maskeli IBAN "
                    + "(ilk 4 + son 4; tam IBAN dönmez), sağlayıcı ve ekstre referansları, tutar (ondalık metin), "
                    + "durum ve zaman damgaları. settlementTasks hakedişin ödemesine bağlı sağlayıcı mutabakat "
                    + "görevlerini (işlem, durum, deneme sayısı, son hata, sonraki deneme) taşır; paymentAttempts "
                    + "atamanın ödeme denemelerinin özetidir (id + durum). releaseOrigin hakedişin nasıl açıldığını "
                    + "söyler (AUTO_COMPLETION zamanlayıcı, EMPLOYER_COMPLETION işverenin tamamlaması, DISPUTE uyuşmazlık "
                    + "çözümü, STAFF personel; bu alan eklenmeden "
                    + "önce açılmış ve uyuşmazlığa bağlanamayan kayıtlarda null). settledAt ekstrede bildirilen "
                    + "gerçekleşme anıdır ve completedAt ile aynı değerdir (yalnız mutabakat onayı yazar); "
                    + "reconciledByStaffId onayı veren personel, bankAccountId hedef hesap. İşçi hesabını silmişse worker "
                    + "null döner. Uç payout.reconcile ile korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hakediş detayı getirildi"),
            @ApiResponse(responseCode = "404", description = "Hakediş bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.PAYOUT_RECONCILE)
    @GetMapping("/admin/payouts/{payoutId}")
    public ResponseEntity<SuccessDataResult<AdminPayoutDetailDto>> detail(@PathVariable UUID payoutId,
                                                                          @AuthenticationPrincipal StaffPrincipal actor) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                staffPayoutService.getForStaff(actor, payoutId),
                AdminMessages.PAYOUT_DETAIL, HttpStatus.OK));
    }

    @Operation(summary = "Banka mutabakatıyla hakedişi tamamlanmış işaretle",
            description = "Ekstredeki gönderim satırının referansı, sağlayıcı işlem kimliği, tutarı, para birimi ve gerçekleşme anı bildirilir. Dördü de payout kaydıyla eşleşmeden durum ilerlemez; eşleşmezse kayıt PROCESSING kalır, FAILED yapılmaz. Aynı ekstre satırı iki kez bildirilirse tek finansal sonuç oluşur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hakediş tamamlandı olarak işaretlendi"),
            @ApiResponse(responseCode = "400", description = "Bildirilen kanıt payout kaydıyla eşleşmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Hakediş bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Hakediş mutabakat beklemiyor, sağlayıcı referansı yok ya da ekstre satırı başka bir hakedişe işlenmiş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.PAYOUT_RECONCILE, action = "payout.reconcile")
    @PostMapping("/admin/payouts/{payoutId}/reconcile")
    public ResponseEntity<SuccessDataResult<PayoutDto>> reconcile(@PathVariable UUID payoutId,
                                                                  @RequestBody @Valid ConfirmPayoutSettlementDto request,
                                                                  @AuthenticationPrincipal StaffPrincipal actor) {
        PayoutDto payout = payoutMapper.toPayoutDto(
                staffPayoutService.confirmSettlement(actor, payoutId, request));

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(
                payout, PayoutMessages.RECONCILED, HttpStatus.OK));
    }

    @Operation(summary = "İşverene iade et",
            description = "Yalnızca para emanetteyken mümkündür; işçiye çıkmış para iade edilemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İade kaydı oluşturuldu"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Para emanette değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.PAYMENT_REFUND, action = "payment.refund")
    @PostMapping("/admin/job-assignments/{assignmentId}/refund")
    public ResponseEntity<SuccessResult> refund(@PathVariable UUID assignmentId,
                                                @RequestBody @Valid RefundRequestDto request,
                                                @AuthenticationPrincipal StaffPrincipal actor) {
        staffPayoutService.refundEmployer(actor, assignmentId, request.getAmount(), request.getReason());

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessResult(
                PayoutMessages.REFUNDED, HttpStatus.OK));
    }
}
