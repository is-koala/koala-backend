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
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.constants.BankAccountMessages;
import com.exskylab.koala.core.dtos.bankAccount.request.ConfirmIbanTransferDto;
import com.exskylab.koala.core.dtos.bankAccount.response.BankAccountDto;
import com.exskylab.koala.core.mappers.BankAccountMapper;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.company.request.ApproveCompanyDto;
import com.exskylab.koala.core.dtos.company.request.RejectCompanyDto;
import com.exskylab.koala.core.dtos.company.response.CompanyReviewDto;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.core.dtos.jobAssignment.response.AdminAssignmentDto;
import com.exskylab.koala.core.dtos.user.request.GrantRoleDto;
import com.exskylab.koala.core.dtos.user.response.RoleGrantDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.core.mappers.RoleGrantMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.core.constants.GalleryMessages;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
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
    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;
    private final GalleryService galleryService;
    private final RoleGrantMapper roleGrantMapper;
    private final JobAssignmentService jobAssignmentService;
    private final BankAccountService bankAccountService;
    private final BankAccountMapper bankAccountMapper;

    public AdminController(CompanyService companyService, UserService userService,
                           CompanyMapper companyMapper, UserMapper userMapper,
                           GalleryService galleryService, RoleGrantMapper roleGrantMapper,
                           JobAssignmentService jobAssignmentService,
                           BankAccountService bankAccountService, BankAccountMapper bankAccountMapper) {
        this.companyService = companyService;
        this.userService = userService;
        this.companyMapper = companyMapper;
        this.userMapper = userMapper;
        this.galleryService = galleryService;
        this.roleGrantMapper = roleGrantMapper;
        this.jobAssignmentService = jobAssignmentService;
        this.bankAccountService = bankAccountService;
        this.bankAccountMapper = bankAccountMapper;
    }

    @Operation(summary = "Atama özeti",
            description = "Uyuşmazlık ve emanet ekranlarının ihtiyaç duyduğu okuma: taraflar, atama ve ödeme durumu, fiyat dökümü. Emanetteki tutar finansal veridir; uç payment.read ile korunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atama özeti döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.PAYMENT_READ)
    @GetMapping("/job-assignments/{assignmentId}")
    public ResponseEntity<SuccessDataResult<AdminAssignmentDto>> assignment(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAssignmentService.getAdminDetail(assignmentId),
                AdminMessages.ASSIGNMENT_DETAIL, HttpStatus.OK));
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

        var page = companyService.searchCompanies(status, query, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent().stream().map(companyMapper::toCompanyReviewDto).toList()),
                AdminMessages.COMPANIES_LISTED, HttpStatus.OK));
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
        return ResponseEntity.ok(new SuccessDataResult<>(
                companyMapper.toCompanyReviewDto(companyService.approveCompany(actor, companyId, note)),
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

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyMapper.toCompanyReviewDto(companyService.rejectCompany(actor, companyId, request.getReason())),
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
}
