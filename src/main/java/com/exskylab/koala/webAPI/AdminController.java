package com.exskylab.koala.webAPI;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.IdentityRevealService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.admin.request.CreatedAtRangeDto;
import com.exskylab.koala.core.dtos.identity.request.RejectIdentityReviewDto;
import com.exskylab.koala.core.dtos.identity.response.AdminIdentityRevealDto;
import com.exskylab.koala.core.dtos.identity.response.IdentityReviewDto;
import com.exskylab.koala.entities.IdentityReviewStatus;
import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.constants.BankAccountMessages;
import com.exskylab.koala.core.constants.IdentityRevealMessages;
import com.exskylab.koala.core.dtos.bankAccount.request.ConfirmIbanTransferDto;
import com.exskylab.koala.core.dtos.bankAccount.response.AdminBankAccountDto;
import com.exskylab.koala.core.dtos.bankAccount.response.BankAccountDto;
import com.exskylab.koala.core.dtos.bankAccount.response.IbanPendingDto;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.entities.BankAccountVerificationStatus;
import com.exskylab.koala.core.mappers.BankAccountMapper;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.company.request.ApproveCompanyDto;
import com.exskylab.koala.core.dtos.company.request.RejectCompanyDto;
import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDetailDto;
import com.exskylab.koala.core.dtos.payment.response.AdminPaymentDto;
import com.exskylab.koala.core.dtos.job.response.AdminJobDetailDto;
import com.exskylab.koala.core.dtos.job.response.AdminJobDto;
import com.exskylab.koala.business.abstracts.AdminDetailService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.TransportService;
import com.exskylab.koala.core.dtos.transport.response.TransportPlanDto;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.core.dtos.user.request.GrantRoleDto;
import com.exskylab.koala.core.dtos.user.response.RoleGrantDto;
import com.exskylab.koala.core.dtos.user.response.AdminUserDetailDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.core.mappers.RoleGrantMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.core.constants.GalleryMessages;
import com.exskylab.koala.core.helpers.SignedFileResponses;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.VerificationDocument;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Yönetim", description = "Yalnızca ADMIN rolüne açık işlemler.")
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final CompanyService companyService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final GalleryService galleryService;
    private final RoleGrantMapper roleGrantMapper;
    private final JobAssignmentService jobAssignmentService;
    private final BankAccountService bankAccountService;
    private final BankAccountMapper bankAccountMapper;
    private final IdentityDocumentReviewService identityDocumentReviewService;
    private final SignedFileResponses signedFileResponses;
    private final AdminDetailService adminDetailService;
    private final JobService jobService;
    private final TransportService transportService;
    private final IdentityRevealService identityRevealService;

    public AdminController(CompanyService companyService, UserService userService, UserMapper userMapper,
                           GalleryService galleryService, RoleGrantMapper roleGrantMapper,
                           JobAssignmentService jobAssignmentService,
                           BankAccountService bankAccountService, BankAccountMapper bankAccountMapper,
                           IdentityDocumentReviewService identityDocumentReviewService,
                           SignedFileResponses signedFileResponses, AdminDetailService adminDetailService,
                           JobService jobService, TransportService transportService,
                           IdentityRevealService identityRevealService) {
        this.companyService = companyService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.galleryService = galleryService;
        this.roleGrantMapper = roleGrantMapper;
        this.jobAssignmentService = jobAssignmentService;
        this.bankAccountService = bankAccountService;
        this.bankAccountMapper = bankAccountMapper;
        this.identityDocumentReviewService = identityDocumentReviewService;
        this.signedFileResponses = signedFileResponses;
        this.adminDetailService = adminDetailService;
        this.jobService = jobService;
        this.transportService = transportService;
        this.identityRevealService = identityRevealService;
    }

    @Operation(summary = "Atama kuyruğu",
            description = "Son atamaları sayfalar. Yanıt AdminAssignmentDto'dur; UUID bakışı ile aynı SafeUser ve fiyat dökümü. "
                    + "Emanet kuralları değişmez. Satır companyId ve companyName taşır (bireysel ilanda ikisi de null). "
                    + "status verilmezse her durum döner; birden çok değer verilebilir. from ve to atamanın oluşturulma "
                    + "anını (createdAt) süzer, ikisi de dahildir ve dilimli ISO-8601 an ister; yalnız biri verilebilir. "
                    + "query (2-100 karakter, kırpılır; kısa ya da boş değer süzmez) atama id'si, "
                    + "ilan başlığı, işveren ve çalışanın ad-soyadı ile e-postasında büyük-küçük harf ve Türkçe karakter "
                    + "duyarsız arar; 100 karakteri aşarsa 400. workerId, employerId ve jobId süzgeçleri birbirleriyle, "
                    + "status, tarih aralığı ve query ile kesişir; kullanıcı ve ilan detayları bu süzgeçlerle buraya bağlanır. "
                    + "Çalışan hesabını silmişse satır listede kalır, worker null döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atamalar listelendi"),
            @ApiResponse(responseCode = "400", description = "query 100 karakteri aşıyor, tarih dilimsiz ya da bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.PAYMENT_READ)
    @GetMapping("/job-assignments")
    public ResponseEntity<SuccessDataResult<PageDto<AdminAssignmentDto>>> assignments(
            @RequestParam(required = false) List<AssignmentStatus> status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID workerId,
            @RequestParam(required = false) UUID employerId,
            @RequestParam(required = false) UUID jobId,
            @ParameterObject CreatedAtRangeDto createdAt,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        var page = jobAssignmentService.listForStaff(status, query, workerId, employerId, jobId,
                createdAt.toRange(), pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent()),
                AdminMessages.ASSIGNMENTS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Atama detayı",
            description = "Uyuşmazlık ve emanet ekranlarının ihtiyaç duyduğu okuma: taraflar, atama ve ödeme durumu, "
                    + "fiyat dökümü. Liste satırının alanlarına ek olarak bağlı kayıtların özetini (id + durum + oluşturulma) "
                    + "taşır: ödeme denemeleri (payments), hakediş (payout, yoksa null), uyuşmazlıklar (disputes) ve "
                    + "taraflar arasındaki ya da ilana bağlı şikayetler (reports). Zaman çizelgesi için application "
                    + "(başvuru; durum, başvuru ve yanıt anı, ret gerekçesi), workSessions (giriş/çıkış anları, dakika, "
                    + "plausibility bayrakları), cancellation (iptal eden taraf ve kullanıcı, gerekçe, bildirim süresi, "
                    + "kesinti ve iade tutarı, refundId; iptal yoksa null) ve reviews (yazar, konu, puan, yorum) ile "
                    + "createdAt, expectedCompletionDateTime, employerType ve notes taşınır. Emanetteki tutar finansal "
                    + "veridir; uç payment.read ile korunur. Konumlar kişisel veridir: personel user.read_pii "
                    + "taşımıyorsa workSessions[].checkIn/checkOut enlem-boylamı ve timeline[] biniş satırlarının "
                    + "latitude/longitude alanı null döner; durağa uzaklık (distanceToStopMeters) ve yön (direction) "
                    + "türetilmiş, kişisel olmayan alanlar olarak kalır. Gizlenen konum \"hiç alınmamış\" konumdan "
                    + "ayrılsın diye timeline[].locationWithheld ve workSessions[].checkInLocationWithheld / "
                    + "checkOutLocationWithheld yalnız kayıtlı bir koordinat maskelendiğinde true olur. Koordinat "
                    + "dönen izinli okuma denetim kaydına user.read_pii olarak yazılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atama detayı döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.PAYMENT_READ)
    @GetMapping("/job-assignments/{assignmentId}")
    public ResponseEntity<SuccessDataResult<AdminAssignmentDetailDto>> assignment(
            @PathVariable UUID assignmentId, @AuthenticationPrincipal StaffPrincipal actor) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.getAssignmentDetail(actor, assignmentId),
                AdminMessages.ASSIGNMENT_DETAIL, HttpStatus.OK));
    }

    @Operation(summary = "Atamanın ödeme denemeleri (personel)",
            description = "Atamaya ait her ödeme denemesini yeniden eskiye sıralar. Her satır durum, tutar dökümü, "
                    + "sağlayıcı referansları (conversationId, providerPaymentId, providerTransactionId), doğrulama "
                    + "sayacı, oluşturulma/güncellenme anları ve iade kayıtlarını taşır. payoutId atamanın hakedişi bu "
                    + "denemeye bağlıysa dolar, diğer denemelerde null. Her iade origin ve originId taşır: iptal kaydı "
                    + "iadeyi gösteriyorsa CANCELLATION (iptal id'si), atamanın REFUND_EMPLOYER ile çözülmüş uyuşmazlığı "
                    + "varsa DISPUTE (uyuşmazlık id'si), aksi hâlde personel iadesi STAFF (originId null). Kart verisi ya "
                    + "da ödeme token'ı dönmez. Uç payment.read ile korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ödeme denemeleri listelendi"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.PAYMENT_READ)
    @GetMapping("/job-assignments/{assignmentId}/payments")
    public ResponseEntity<SuccessDataResult<List<AdminPaymentDto>>> assignmentPayments(
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.getPaymentsForAssignment(assignmentId),
                AdminMessages.PAYMENTS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Ödeme denemeleri listesi (personel)",
            description = "Bütün atamaların ödeme denemelerini sayfalar; takılı ve başarısız ödemeleri bulmak için. "
                    + "status verilmezse her durum döner; birden çok değer verilebilir (INITIATED, VERIFY_PENDING, "
                    + "IN_REVIEW, SUCCEEDED, FAILED). Satırlar tekil ödeme detayıyla aynı AdminPaymentDto'dur: bağlı "
                    + "atama/ilan/şirket (companyId, companyName), taraflar (SafeUser; e-posta yok), durum, tutar dökümü, "
                    + "sağlayıcı referansları, doğrulama sayacı ve sonraki doğrulama anı (nextVerificationAt; "
                    + "VERIFY_PENDING'de null ise deneme zamanlayıcıdan düşmüştür), payoutId ve iade kayıtları. "
                    + "Sıralama createdAt (varsayılan, yeniden eskiye), updatedAt, status ve nextVerificationAt "
                    + "alanlarıyla; en eskiyi başa almak için sort=createdAt,asc. Kart verisi ya da ödeme token'ı "
                    + "dönmez. Uç payment.read ile korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ödeme denemeleri listelendi"),
            @ApiResponse(responseCode = "400", description = "Bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.PAYMENT_READ)
    @GetMapping("/payments")
    public ResponseEntity<SuccessDataResult<PageDto<AdminPaymentDto>>> payments(
            @RequestParam(required = false) List<PaymentAttemptStatus> status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        var page = adminDetailService.searchPayments(status, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent()),
                AdminMessages.PAYMENTS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Ödeme denemesi detayı",
            description = "Tek bir ödeme denemesi: bağlı atama/ilan/taraflar (SafeUser; e-posta yok), durum, tutar dökümü, "
                    + "sağlayıcı referansları, doğrulama sayacı, oluşturulma/güncellenme anları, payoutId (hakediş bu "
                    + "ödemeye bağlıysa) ve iade kayıtları; iadelerde origin/originId listedeki kuralla türetilir "
                    + "(CANCELLATION, DISPUTE ya da STAFF). Kart verisi ya da ödeme token'ı dönmez. Uç payment.read ile "
                    + "korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ödeme denemesi getirildi"),
            @ApiResponse(responseCode = "404", description = "Ödeme kaydı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.PAYMENT_READ)
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<SuccessDataResult<AdminPaymentDto>> payment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.getPayment(paymentId),
                AdminMessages.PAYMENT_DETAIL, HttpStatus.OK));
    }

    @Operation(summary = "İlan listesi (personel)",
            description = "İlanları durum süzgeci ve aramayla sayfalar; status verilmezse her durum döner. "
                    + "query (2-100 karakter, kırpılır; kısa ya da boş değer süzmez) ilan id'si, ilan başlığı, şirket adı, "
                    + "işverenin ad-soyadı ile e-postasında büyük-küçük harf ve Türkçe karakter duyarsız arar; "
                    + "100 karakteri aşarsa 400. companyId ve employerId süzgeçleri status ve query ile kesişir; "
                    + "şirket ve kullanıcı detayları ilanlarına bu süzgeçlerle bağlanır. Satır işvereni id, ad ve "
                    + "e-postayla, başvuru ve atama sayılarıyla taşır. Uç job.read ile korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlanlar listelendi"),
            @ApiResponse(responseCode = "400", description = "query 100 karakteri aşıyor ya da bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.JOB_READ)
    @GetMapping("/jobs")
    public ResponseEntity<SuccessDataResult<PageDto<AdminJobDto>>> jobs(
            @RequestParam(required = false) List<JobStatus> status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID employerId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.searchForStaff(status, query, companyId, employerId, pageable),
                AdminMessages.JOBS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İlan detayı (personel)",
            description = "İlanın tamamı: açıklama, kategori, fiyat dökümü, işveren (id, ad, e-posta) ve şirket, "
                    + "başvuru ve atama sayıları, görseller. Adres personele tam hâliyle döner; ADR-0003'ün yaklaşık "
                    + "konum kuralı kullanıcı yüzeyi içindir ve personel istisnadır. Uç job.read ile korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İlan detayı getirildi"),
            @ApiResponse(responseCode = "404", description = "İlan bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.JOB_READ)
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<SuccessDataResult<AdminJobDetailDto>> job(@PathVariable UUID jobId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobService.getAdminDetail(jobId),
                AdminMessages.JOB_DETAIL, HttpStatus.OK));
    }

    @Operation(summary = "İlanın ulaşım planı (personel)",
            description = "İşveren görünümüyle aynı gövde: plaka, şoför, tam durak adresleri ve servis/durak "
                    + "başına seçilmiş koltuk sayısı. Uç job.read ile korunur; personel ilanın işvereni olmak zorunda "
                    + "değildir. İlan yoksa JOB_NOT_FOUND, plan yoksa TRANSPORT_PLAN_NOT_FOUND.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ulaşım planı getirildi"),
            @ApiResponse(responseCode = "404", description = "İlan veya plan bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.JOB_READ)
    @GetMapping("/jobs/{jobId}/transport-plan")
    public ResponseEntity<SuccessDataResult<TransportPlanDto>> jobTransportPlan(@PathVariable UUID jobId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                transportService.getPlanForStaff(jobId),
                AdminMessages.JOB_TRANSPORT_PLAN, HttpStatus.OK));
    }

    @Operation(summary = "Görseli her yerden kaldır",
            description = "Görseli bulunduğu tüm ilan ve şirket galerilerinden düşürür, medya kaydını soft delete eder. Depodaki nesne silinmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Görsel kaldırıldı"),
            @ApiResponse(responseCode = "404", description = "Görsel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.CONTENT_MODERATE, action = "image.remove")
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Result> removeImage(@PathVariable UUID imageId) {
        galleryService.removeImageEverywhere(imageId);
        return ResponseEntity.ok(new SuccessResult(GalleryMessages.IMAGE_REMOVED, HttpStatus.OK));
    }

    @Operation(summary = "Bekleyen IBAN doğrulamaları",
            description = "status verilmezse ya da PENDING ise süresi dolmamış bekleyen transferler sayfalanır. "
                    + "Yanıt maskeli IBAN ve referans taşır; tam IBAN yoktur. Onay mevcut confirm ucundan yapılır; ret ucu yoktur. "
                    + "query (2-100 karakter, kırpılır; kısa ya da boş değer süzmez) hesap sahibinin ad-soyadı, e-postası ve "
                    + "doğrulama referansında büyük-küçük harf ve Türkçe karakter duyarsız arar; 100 karakteri aşarsa 400. "
                    + "userId verilirse yalnız o kullanıcının bekleyen doğrulamaları döner; query ile kesişir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bekleyen doğrulamalar listelendi"),
            @ApiResponse(responseCode = "400", description = "Yalnız PENDING kuyruğu vardır ya da query 100 karakteri aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.BANK_ACCOUNT_VERIFY)
    @GetMapping("/bank-account-verifications")
    public ResponseEntity<SuccessDataResult<PageDto<IbanPendingDto>>> pendingIbanVerifications(
            @RequestParam(defaultValue = "PENDING") BankAccountVerificationStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID userId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        if (status != BankAccountVerificationStatus.PENDING) {
            throw new ValidationException(BankAccountMessages.PENDING_QUEUE_ONLY);
        }
        var page = bankAccountService.listPendingForStaff(query, userId, pageable);
        int attemptsAllowed = bankAccountService.verificationAttemptsAllowed();
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent().stream()
                        .map(account -> bankAccountMapper.toIbanPendingDto(account, attemptsAllowed))
                        .toList()),
                BankAccountMessages.PENDING_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "IBAN doğrulama transferini onayla",
            description = "Ekstredeki gelen transferin gönderen IBAN'ı ve tutarı bildirilir. Gönderen IBAN kayıtlı IBAN'la eşleşmezse ya da tutar tutmazsa doğrulama denemesi düşer. Ekstre bir hesap sahibi adı taşıyorsa gönderilebilir; gönderilmezse ad doğrulanmamış kalır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doğrulama tamamlandı"),
            @ApiResponse(responseCode = "400", description = "Gönderen IBAN ya da tutar eşleşmiyor, doğrulama süresi dolmuş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Referansla eşleşen bekleyen doğrulama yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Hesap zaten doğrulanmış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.BANK_ACCOUNT_VERIFY, action = "bank.account.verify")
    @PostMapping("/bank-account-verifications/{reference}/confirm")
    public ResponseEntity<SuccessDataResult<BankAccountDto>> confirmIbanVerification(
            @PathVariable String reference, @RequestBody @Valid ConfirmIbanTransferDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                bankAccountMapper.toBankAccountDto(bankAccountService.confirmVerification(reference, request)),
                BankAccountMessages.VERIFICATION_CONFIRMED, HttpStatus.OK));
    }

    @Operation(summary = "Şirketleri onay durumuna göre listele",
            description = "status verilmezse onay bekleyenler döner. query, şirket adı ve vergi numarasında arar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirketler listelendi"),
            @ApiResponse(responseCode = "400", description = "Bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.COMPANY_APPROVE)
    @GetMapping("/companies")
    public ResponseEntity<SuccessDataResult<PageDto<CompanyReviewDto>>> companies(
            @RequestParam(defaultValue = "PENDING") CompanyApprovalStatus status,
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = adminDetailService.searchCompanies(status, query, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent()),
                AdminMessages.COMPANIES_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şirket incelemesi",
            description = "Kuyruk satırıyla aynı CompanyReviewDto: adres, galeri kimlikleri, onay durumu. "
                    + "Onay ve red ayrı uçlardan; bu GET yalnız okur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirket getirildi"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.COMPANY_APPROVE)
    @GetMapping("/companies/{companyId}")
    public ResponseEntity<SuccessDataResult<CompanyReviewDto>> company(@PathVariable UUID companyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.getCompany(companyId),
                AdminMessages.COMPANY_FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "Şirket galeri görselini indir",
            description = "Depo imzalı adres destekliyorsa kısa ömürlü imzalı adrese yönlendirir, desteklemiyorsa baytları akıtır. Kuyruk yanıtı depo anahtarı taşımaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dosya akıtıldı"),
            @ApiResponse(responseCode = "302", description = "Kısa ömürlü imzalı adrese yönlendirildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Şirket ya da görsel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.COMPANY_APPROVE, action = "company.image.download")
    @GetMapping("/companies/{companyId}/images/{imageId}/download")
    public ResponseEntity<?> downloadCompanyImage(@PathVariable UUID companyId, @PathVariable UUID imageId) {
        Image image = galleryService.getCompanyImageForStaffDownload(companyId, imageId);
        return signedFileResponses.attachment(image.getFileUrl(), image.getFileName());
    }

    @Operation(summary = "Şirketi onayla",
            description = "Gövde isteğe bağlıdır; note verilirse onay gerekçesi kayda geçer. "
                    + "Reddedilmiş bir şirket de onaylanabilir, onaylanmış şirket 409 alır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirket onaylandı"),
            @ApiResponse(responseCode = "400", description = "Gerekçe 1000 karakteri aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Şirket zaten onaylı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.COMPANY_APPROVE, action = "company.approve")
    @PostMapping("/companies/{companyId}/approve")
    public ResponseEntity<SuccessDataResult<CompanyReviewDto>> approveCompany(
            @PathVariable UUID companyId,
            @RequestBody(required = false) @Valid ApproveCompanyDto request,
            @AuthenticationPrincipal StaffPrincipal actor) {

        String note = request == null ? null : request.getNote();
        companyService.approveCompany(actor, companyId, note);
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.getCompany(companyId),
                AdminMessages.COMPANY_APPROVED, HttpStatus.OK));
    }

    @Operation(summary = "Şirketi reddet",
            description = "Gerekçe zorunludur ve kayda geçer. Reddedilen şirket onay kuyruğundan düşer; "
                    + "status=REJECTED ile listelenir. Onaylanmış bir şirket buradan reddedilemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirket reddedildi"),
            @ApiResponse(responseCode = "400", description = "Gerekçe boş ya da çok uzun",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Şirket zaten reddedilmiş ya da onaylanmış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.COMPANY_APPROVE, action = "company.reject")
    @PostMapping("/companies/{companyId}/reject")
    public ResponseEntity<SuccessDataResult<CompanyReviewDto>> rejectCompany(
            @PathVariable UUID companyId,
            @RequestBody @Valid RejectCompanyDto request,
            @AuthenticationPrincipal StaffPrincipal actor) {

        companyService.rejectCompany(actor, companyId, request.getReason());
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.getCompany(companyId),
                AdminMessages.COMPANY_REJECTED, HttpStatus.OK));
    }

    @Operation(summary = "Kullanıcıları ara",
            description = "query verilmezse tüm kullanıcılar sayfalanarak döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kullanıcılar listelendi")
    })
    @RequiresPermission(StaffPermission.USER_READ)
    @GetMapping("/users")
    public ResponseEntity<SuccessDataResult<PageDto<SafeUserDto>>> users(
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = userService.search(query, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent().stream().map(userMapper::toSafeUserDto).toList()),
                AdminMessages.USERS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kullanıcı detayı",
            description = "SafeUserDto'nun üst kümesi: doğrulama bayrakları (e-posta, telefon, kimlik), işveren "
                    + "bayrağı, kayıt tarihi, şirket üyelikleri (id, ad, rol), rozetler, geçmiş sayaçları "
                    + "(başvuru, işçi/işveren olarak atama, ödeme, hakediş, uyuşmazlık, alınan/yapılan şikayet), "
                    + "son kimlik incelemesinin id'si ve durumu (identityReviewId, identityReviewStatus; inceleme yoksa "
                    + "ikisi de null), açık hesap silme talebinin id'si ve oturum özeti (sessionSummary: etkin oturum "
                    + "sayısı, son oturum açılışı, son oturumun cihaz platformu; cihaz kimliği dönmez, oturum yoksa "
                    + "sayaç 0 ve diğer ikisi null). email ve phoneNumber "
                    + "yalnız user.read_pii taşıyan personele döner; taşımayana null gelir ve gövde aynı şekildedir. "
                    + "PII döndüğünde denetim satırı user.read_pii izniyle (PERSONAL_DATA) yazılır. Sayaçlar "
                    + "süzgeçli listelere bağlanır: /admin/job-assignments?workerId=|employerId=, "
                    + "/admin/payouts?workerId=, /admin/disputes?userId=, /admin/reports?reportedUserId=|reporterId=. "
                    + "Rol listesi /users/{userId}/roles'ta kalır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kullanıcı getirildi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.USER_READ)
    @GetMapping("/users/{userId}")
    public ResponseEntity<SuccessDataResult<AdminUserDetailDto>> user(@PathVariable UUID userId,
                                                                      @AuthenticationPrincipal StaffPrincipal actor) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.getUserDetail(actor, userId),
                AdminMessages.USER_FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "Kullanıcının banka hesapları (personel)",
            description = "Kullanıcının etkin banka hesapları: birincil önce, sonra eskiden yeniye. Her satır maskeli "
                    + "IBAN (ilk 4 + *** + son 4; tam IBAN hiçbir zaman dönmez), etkin doğrulama durumu (süresi dolan "
                    + "PENDING EXPIRED olarak gelir), primary bayrağı, hesap sahibi adı doğrulama bayrağı, verifiedAt ve "
                    + "createdAt taşır. Bekleyen doğrulamanın referansı ve tutarı bu uçta yoktur; "
                    + "/admin/bank-account-verifications?userId= ile bank.account.verify altında okunur. Uç "
                    + "user.read_pii ile korunur (PERSONAL_DATA); her okuma denetim satırı yazar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Banka hesapları listelendi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.USER_READ_PII)
    @GetMapping("/users/{userId}/bank-accounts")
    public ResponseEntity<SuccessDataResult<List<AdminBankAccountDto>>> userBankAccounts(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.getBankAccountsOfUser(userId),
                AdminMessages.USER_BANK_ACCOUNTS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kullanıcının kimlik bilgilerini kim görüntüledi (personel)",
            description = "İşverenlerin GET /job-assignments/{id}/worker-identity ile bu kullanıcının kimlik bilgilerini "
                    + "görüntüleme kayıtları, yeniden eskiye. Her satır görüntüleyenin id'si ve ad soyadı, atama ve ilan "
                    + "kimliği, ilan başlığı, an ve maskeli IP taşır; TC kimlik numarası dönmez. Yalnız revealedAt ile "
                    + "sıralanabilir, bilinmeyen sıralama alanı 400. Görüntüleyen hesabını silmişse satır kalır, viewerId "
                    + "durur ve viewerFullName null döner. Uç user.read_pii ile korunur (PERSONAL_DATA); her okuma denetim "
                    + "satırı yazar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Görüntüleme kayıtları listelendi"),
            @ApiResponse(responseCode = "400", description = "Bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.USER_READ_PII)
    @GetMapping("/users/{userId}/identity-reveals")
    public ResponseEntity<SuccessDataResult<PageDto<AdminIdentityRevealDto>>> userIdentityReveals(
            @PathVariable UUID userId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = identityRevealService.listForStaff(userId, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(PageDto.of(page, page.getContent()),
                IdentityRevealMessages.LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kullanıcının rollerini listele",
            description = "Her rolün ne zaman ve kim tarafından verildiğini gösterir; kimsenin vermediği rollerde grantedBy boştur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roller listelendi"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.USER_READ)
    @GetMapping("/users/{userId}/roles")
    public ResponseEntity<SuccessDataResult<List<RoleGrantDto>>> userRoles(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                roleGrantMapper.toRoleGrantDtoList(userService.getRoleGrants(userId)),
                AdminMessages.USER_ROLES_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kullanıcıya rol ver",
            description = "İdempotenttir: kullanıcı rolü zaten taşıyorsa ikinci satır açılmaz ve mevcut rol verme kaydı korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol verildi"),
            @ApiResponse(responseCode = "400", description = "Rol geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Sistem kullanıcısının rolleri değiştirilemez",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.USER_ROLE_MANAGE, action = "user.role.grant")
    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<SuccessDataResult<List<RoleGrantDto>>> grantUserRole(
            @PathVariable UUID userId, @RequestBody @Valid GrantRoleDto request,
            @AuthenticationPrincipal StaffPrincipal actor) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                roleGrantMapper.toRoleGrantDtoList(userService.grantRole(actor, userId, request.getRole())),
                AdminMessages.USER_ROLE_GRANTED, HttpStatus.OK));
    }

    @Operation(summary = "Kullanıcının rolünü geri al",
            description = "Yönetici kendi ROLE_ADMIN rolünü, sistemdeki son ROLE_ADMIN rolünü ve ROLE_USER rolünü buradan alamaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol geri alındı"),
            @ApiResponse(responseCode = "400", description = "Rol geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Rol korunuyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.USER_ROLE_MANAGE, action = "user.role.revoke")
    @DeleteMapping("/users/{userId}/roles/{role}")
    public ResponseEntity<Result> revokeUserRole(@PathVariable UUID userId, @PathVariable Role role,
                                                 @AuthenticationPrincipal StaffPrincipal actor) {
        userService.revokeRole(actor, userId, role);
        return ResponseEntity.ok(new SuccessResult(AdminMessages.USER_ROLE_REVOKED, HttpStatus.OK));
    }

    @Operation(summary = "TCKK kimlik incelemelerini listele",
            description = "status verilmezse onay bekleyenler döner. query (2-100 karakter, kırpılır; kısa ya da boş değer "
                    + "süzmez) kullanıcının profil ad-soyadı, e-postası ve belgede beyan edilen ad-soyadta büyük-küçük harf "
                    + "ve Türkçe karakter duyarsız arar; status süzgeciyle kesişir; 100 karakteri aşarsa 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İncelemeler listelendi"),
            @ApiResponse(responseCode = "400", description = "query 100 karakteri aşıyor ya da bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.IDENTITY_REVIEW)
    @GetMapping("/identity-verifications")
    public ResponseEntity<SuccessDataResult<PageDto<IdentityReviewDto>>> identityReviews(
            @RequestParam(defaultValue = "PENDING") IdentityReviewStatus status,
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = identityDocumentReviewService.search(status, query, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, adminDetailService.identityReviews(page.getContent())),
                AdminMessages.IDENTITY_REVIEWS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "TCKK kimlik incelemesi",
            description = "Kuyruk satırıyla aynı IdentityReviewDto. Onay ve red ayrı uçlardan; bu GET yalnız okur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İnceleme getirildi"),
            @ApiResponse(responseCode = "404", description = "İnceleme bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.IDENTITY_REVIEW)
    @GetMapping("/identity-verifications/{reviewId}")
    public ResponseEntity<SuccessDataResult<IdentityReviewDto>> identityReview(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.identityReview(identityDocumentReviewService.getById(reviewId)),
                AdminMessages.IDENTITY_REVIEW_FETCHED, HttpStatus.OK));
    }

    @Operation(summary = "TCKK kimlik incelemesini onayla",
            description = "Kullanıcının identityVerified damgası çip olmadan konur. TCKN benzersizliği durur. "
                    + "Onay yalnız bekleyen ve dört fotoğrafı (yüz, TCKK ön, TCKK arka, TCKK yüzün altında) "
                    + "sunucuda kayıtlı olan inceleme için kabul edilir; istek gövdesi okunmaz, kanıt veritabanından "
                    + "yeniden doğrulanır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kimlik onaylandı"),
            @ApiResponse(responseCode = "404", description = "İnceleme bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "İnceleme sonuçlanmış (IDENTITY_REVIEW_ALREADY_DECIDED), "
                    + "dört fotoğraftan biri eksik (IDENTITY_REVIEW_DOCUMENTS_MISSING) ya da TCKN kullanımda "
                    + "(TC_IDENTITY_NUMBER_ALREADY_IN_USE); ret ucu eksik fotoğrafta açık kalır",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.IDENTITY_REVIEW, action = "identity.approve")
    @PostMapping("/identity-verifications/{reviewId}/approve")
    public ResponseEntity<SuccessDataResult<IdentityReviewDto>> approveIdentityReview(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal StaffPrincipal actor) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.identityReview(identityDocumentReviewService.approve(actor, reviewId)),
                AdminMessages.IDENTITY_REVIEW_APPROVED, HttpStatus.OK));
    }

    @Operation(summary = "TCKK kimlik incelemesini reddet",
            description = "Gerekçe zorunludur. Kullanıcı identityVerified olmadan kalır ve yeniden yükleyebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İnceleme reddedildi"),
            @ApiResponse(responseCode = "400", description = "Gerekçe boş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "İnceleme bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "İnceleme sonuçlanmış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.IDENTITY_REVIEW, action = "identity.reject")
    @PostMapping("/identity-verifications/{reviewId}/reject")
    public ResponseEntity<SuccessDataResult<IdentityReviewDto>> rejectIdentityReview(
            @PathVariable UUID reviewId,
            @RequestBody @Valid RejectIdentityReviewDto request,
            @AuthenticationPrincipal StaffPrincipal actor) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                adminDetailService.identityReview(identityDocumentReviewService.reject(actor, reviewId, request.getReason())),
                AdminMessages.IDENTITY_REVIEW_REJECTED, HttpStatus.OK));
    }

    @Operation(summary = "TCKK fotoğrafını indir",
            description = "Depo imzalı adres destekliyorsa kısa ömürlü imzalı adrese yönlendirir, desteklemiyorsa baytları akıtır. Tekil okuma olduğu için denetim kaydına girer. Kuyruk yanıtı depo anahtarı taşımaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dosya akıtıldı"),
            @ApiResponse(responseCode = "302", description = "Kısa ömürlü imzalı adrese yönlendirildi",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "İnceleme ya da belge bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.IDENTITY_REVIEW, action = "identity.document.download")
    @GetMapping("/identity-verifications/{reviewId}/documents/{documentId}/download")
    public ResponseEntity<?> downloadIdentityDocument(@PathVariable UUID reviewId, @PathVariable UUID documentId) {
        VerificationDocument document = identityDocumentReviewService.getDocumentForStaffDownload(reviewId, documentId);
        return signedFileResponses.attachment(document.getFileUrl(), document.getFileName());
    }
}
