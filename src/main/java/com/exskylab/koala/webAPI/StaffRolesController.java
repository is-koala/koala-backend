package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.StaffAuthorizationService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.staff.request.GrantStaffPermissionDto;
import com.exskylab.koala.core.dtos.staff.request.IncludeStaffRoleDto;
import com.exskylab.koala.core.dtos.staff.response.StaffRoleDetailDto;
import com.exskylab.koala.core.dtos.staff.response.StaffRoleDto;
import com.exskylab.koala.core.mappers.StaffRoleMapper;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.StaffPermission;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Personel rolleri", description = "Rol→izin DAG'ının okunması ve düzenlenmesi.")
@RestController
@RequestMapping("/admin/roles")
public class StaffRolesController {

    private final StaffAuthorizationService staffAuthorizationService;
    private final StaffRoleMapper staffRoleMapper;

    public StaffRolesController(StaffAuthorizationService staffAuthorizationService,
                                StaffRoleMapper staffRoleMapper) {
        this.staffAuthorizationService = staffAuthorizationService;
        this.staffRoleMapper = staffRoleMapper;
    }

    @Operation(summary = "Personel rollerini listele",
            description = "Her satır rolün kendi izinlerini ve doğrudan kapsadığı rolleri taşır; "
                    + "panel DAG'ın tamamını tek istekte çizebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roller listelendi")
    })
    @RequiresPermission(StaffPermission.ROLE_MANAGE)
    @GetMapping
    public ResponseEntity<SuccessDataResult<List<StaffRoleDto>>> roles() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                staffRoleMapper.toRoleDtoList(staffAuthorizationService.getRoles()),
                StaffMessages.ROLES_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Rolün izinlerini ve kalıtımını göster",
            description = "Rolün kendi izinleri, doğrudan kapsadığı roller, geçişli olarak ulaştığı roller ve "
                    + "DAG aşağı gezilerek çözülmüş etkili izinleri.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol çözüldü"),
            @ApiResponse(responseCode = "404", description = "Rol bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.ROLE_MANAGE)
    @GetMapping("/{roleId}")
    public ResponseEntity<SuccessDataResult<StaffRoleDetailDto>> role(@PathVariable UUID roleId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                staffRoleMapper.toRoleDetailDto(staffAuthorizationService.getRole(roleId)),
                StaffMessages.ROLE_RESOLVED, HttpStatus.OK));
    }

    @Operation(summary = "Role izin ver",
            description = "Sahip olmadığınız bir izni bir role veremezsiniz; kural servis katmanında, "
                    + "aktörün izinleri her yazımda veritabanından yeniden çözülerek uygulanır. "
                    + "İdempotenttir: rol izni zaten taşıyorsa ikinci satır açılmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İzin verildi"),
            @ApiResponse(responseCode = "400", description = "İzin kodu eksik ya da tanınmıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Rol bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.ROLE_MANAGE, action = "staff.role.permission.grant")
    @PostMapping("/{roleId}/permissions")
    public ResponseEntity<Result> grantPermission(
            @AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID roleId,
            @RequestBody @Valid GrantStaffPermissionDto request) {

        staffAuthorizationService.grantPermission(principal, roleId, permissionOf(request.getPermission()));
        return ResponseEntity.ok(new SuccessResult(StaffMessages.PERMISSION_GRANTED, HttpStatus.OK));
    }

    @Operation(summary = "Rolün iznini geri al",
            description = "İdempotenttir: rol izni taşımıyorsa sessizce geçilir. "
                    + "Sahip olmadığınız bir izni bir rolden alamazsınız.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İzin geri alındı"),
            @ApiResponse(responseCode = "404", description = "Rol ya da izin kodu bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.ROLE_MANAGE, action = "staff.role.permission.revoke")
    @DeleteMapping("/{roleId}/permissions/{permission}")
    public ResponseEntity<Result> revokePermission(
            @AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID roleId,
            @PathVariable String permission) {

        staffAuthorizationService.revokePermission(principal, roleId, permissionOf(permission));
        return ResponseEntity.ok(new SuccessResult(StaffMessages.PERMISSION_REVOKED, HttpStatus.OK));
    }

    @Operation(summary = "Role başka bir rolü kapsat",
            description = "DAG'a kenar ekler: kapsayan rol, kapsanan rolün izinlerine geçişli olarak ulaşır. "
                    + "Döngü oluşturacak kapsama 409 ile reddedilir; kapsanan rolün etkili izinlerinin "
                    + "tamamına sahip olmayan aktör de reddedilir. Görev ayrılığı gereği OWNER dışında hiçbir "
                    + "rol hem trust hem finans dalını kapsayamaz. İdempotenttir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kapsama eklendi"),
            @ApiResponse(responseCode = "400", description = "Kapsanacak rol belirtilmemiş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Rol bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409",
                    description = "Kapsama rol grafiğinde döngü oluştururdu ya da görev ayrılığını bozardı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.ROLE_MANAGE, action = "staff.role.include.add")
    @PostMapping("/{roleId}/includes")
    public ResponseEntity<Result> includeRole(
            @AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID roleId,
            @RequestBody @Valid IncludeStaffRoleDto request) {

        staffAuthorizationService.includeRole(principal, roleId, request.getIncludedRoleId());
        return ResponseEntity.ok(new SuccessResult(StaffMessages.ROLE_INCLUDED, HttpStatus.OK));
    }

    @Operation(summary = "Rolün kapsamasını kaldır",
            description = "DAG'dan kenar siler. İdempotenttir: kenar yoksa sessizce geçilir. "
                    + "Kapsanan rolün etkili izinlerinin tamamına sahip olmayan aktör reddedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kapsama kaldırıldı"),
            @ApiResponse(responseCode = "404", description = "Rol bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.ROLE_MANAGE, action = "staff.role.include.remove")
    @DeleteMapping("/{roleId}/includes/{includedRoleId}")
    public ResponseEntity<Result> excludeRole(
            @AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID roleId,
            @PathVariable UUID includedRoleId) {

        staffAuthorizationService.excludeRole(principal, roleId, includedRoleId);
        return ResponseEntity.ok(new SuccessResult(StaffMessages.ROLE_EXCLUDED, HttpStatus.OK));
    }

    private static StaffPermission permissionOf(String code) {
        return StaffPermission.fromCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(StaffMessages.PERMISSION_UNKNOWN));
    }
}
