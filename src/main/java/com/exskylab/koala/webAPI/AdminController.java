package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.AdminMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
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

    public AdminController(CompanyService companyService, UserService userService,
                           CompanyMapper companyMapper, UserMapper userMapper,
                           GalleryService galleryService, RoleGrantMapper roleGrantMapper) {
        this.companyService = companyService;
        this.userService = userService;
        this.companyMapper = companyMapper;
        this.userMapper = userMapper;
        this.galleryService = galleryService;
        this.roleGrantMapper = roleGrantMapper;
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

    @Operation(summary = "Şirketleri onay durumuna göre listele",
            description = "approved=false onay bekleyen şirketleri verir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirketler listelendi")
    })
    @RequiresPermission(StaffPermission.COMPANY_APPROVE)
    @GetMapping("/companies")
    public ResponseEntity<SuccessDataResult<PageDto<CompanyDto>>> companies(
            @RequestParam(defaultValue = "false") boolean approved,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = companyService.getCompaniesByApproval(approved, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, page.getContent().stream().map(companyMapper::toCompanyDto).toList()),
                AdminMessages.COMPANIES_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Şirketi onayla",
            description = "Onaylanana kadar şirket ilan yayınlayamaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Şirket onaylandı"),
            @ApiResponse(responseCode = "404", description = "Şirket bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.COMPANY_APPROVE, action = "company.approve")
    @PostMapping("/companies/{companyId}/approve")
    public ResponseEntity<SuccessDataResult<CompanyDto>> approveCompany(@PathVariable UUID companyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                companyMapper.toCompanyDto(companyService.approveCompany(companyId)),
                AdminMessages.COMPANY_APPROVED, HttpStatus.OK));
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
            @PathVariable UUID userId, @RequestBody @Valid GrantRoleDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                roleGrantMapper.toRoleGrantDtoList(userService.grantRole(userId, request.getRole())),
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
    public ResponseEntity<Result> revokeUserRole(@PathVariable UUID userId, @PathVariable Role role) {
        userService.revokeRole(userId, role);
        return ResponseEntity.ok(new SuccessResult(AdminMessages.USER_ROLE_REVOKED, HttpStatus.OK));
    }
}
