package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.staff.request.CreateStaffMemberDto;
import com.exskylab.koala.core.dtos.staff.request.ResetStaffPasswordDto;
import com.exskylab.koala.core.dtos.staff.request.UpdateStaffMemberDto;
import com.exskylab.koala.core.dtos.staff.response.StaffMemberDto;
import com.exskylab.koala.core.mappers.StaffMemberMapper;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.StaffPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Personel yönetimi", description = "Personel kayıtları, rol üyeliği ve parola atama.")
@RestController
@RequestMapping("/admin/staff")
public class StaffMembersController {

    private final StaffAccountService staffAccountService;
    private final StaffMemberMapper staffMemberMapper;

    public StaffMembersController(StaffAccountService staffAccountService, StaffMemberMapper staffMemberMapper) {
        this.staffAccountService = staffAccountService;
        this.staffMemberMapper = staffMemberMapper;
    }

    @Operation(summary = "Personeli listele",
            description = "E-posta ve görünen ad üzerinde arar; her satır personelin rol üyeliklerini taşır.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Personel listelendi"))
    @RequiresPermission(StaffPermission.STAFF_READ)
    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<StaffMemberDto>>> list(
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(staffAccountService.search(query, pageable), staffMemberMapper::toMemberDto),
                StaffMessages.MEMBERS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Personel kaydını göster")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personel döndü"),
            @ApiResponse(responseCode = "404", description = "Personel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.STAFF_READ)
    @GetMapping("/{staffId}")
    public ResponseEntity<SuccessDataResult<StaffMemberDto>> member(@PathVariable UUID staffId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                staffMemberMapper.toMemberDto(staffAccountService.get(staffId)),
                StaffMessages.MEMBER_RESOLVED, HttpStatus.OK));
    }

    @Operation(summary = "Personel oluştur",
            description = "Parolayı yönetici atar; hesap ilk girişte parola değiştirme bayrağıyla doğar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Personel oluşturuldu"),
            @ApiResponse(responseCode = "409", description = "Bu e-posta ile kayıtlı personel zaten var",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.STAFF_MANAGE, action = "staff.member.create")
    @PostMapping
    public ResponseEntity<SuccessDataResult<StaffMemberDto>> create(
            @Valid @RequestBody CreateStaffMemberDto request) {

        StaffMemberDto created = staffMemberMapper.toMemberDto(staffAccountService.create(
                request.getEmail(), request.getDisplayName(), request.getPassword()));

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<>(created, StaffMessages.MEMBER_CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Personeli devre dışı bırak ya da aç",
            description = "Devre dışı bırakmak personelin açık token'larını da geçersiz kılar. "
                    + "Sistemde yönetim izni taşıyan etkin personel kalmayacaksa istek 409 ile reddedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personel güncellendi"),
            @ApiResponse(responseCode = "404", description = "Personel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Son yönetici devre dışı bırakılamaz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.STAFF_MANAGE, action = "staff.member.update")
    @PatchMapping("/{staffId}")
    public ResponseEntity<SuccessDataResult<StaffMemberDto>> update(
            @AuthenticationPrincipal StaffPrincipal actor, @PathVariable UUID staffId,
            @Valid @RequestBody UpdateStaffMemberDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                staffMemberMapper.toMemberDto(
                        staffAccountService.setActive(actor, staffId, request.getActive())),
                StaffMessages.MEMBER_UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Personele rol ver",
            description = "Aktör, verdiği rolün etkili izinlerinin tamamını taşımak zorundadır. Görev ayrılığı "
                    + "gereği OWNER dışında hiçbir personel hem trust hem finans dalına ulaşamaz. İdempotenttir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol verildi"),
            @ApiResponse(responseCode = "403", description = "Aktör rolün izinlerinin tamamını taşımıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Personel ya da rol bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Üyelik görev ayrılığını bozardı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.STAFF_MANAGE, action = "staff.member.role.grant")
    @PostMapping("/{staffId}/roles/{roleCode}")
    public ResponseEntity<Result> grantRole(@AuthenticationPrincipal StaffPrincipal actor,
                                            @PathVariable UUID staffId, @PathVariable String roleCode) {

        staffAccountService.grantRole(actor, staffId, roleCode);
        return ResponseEntity.ok(new SuccessResult(StaffMessages.MEMBER_ROLE_GRANTED, HttpStatus.OK));
    }

    @Operation(summary = "Personelin rolünü geri al",
            description = "Aktör kendi son yönetim üyeliğini bırakamaz ve sistemde yönetim izni taşıyan "
                    + "etkin personel kalmayacaksa istek 409 ile reddedilir. İdempotenttir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol geri alındı"),
            @ApiResponse(responseCode = "403", description = "Aktör rolün izinlerinin tamamını taşımıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Personel ya da rol bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Kendini kilitleme ya da son yönetici koruması",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.STAFF_MANAGE, action = "staff.member.role.revoke")
    @DeleteMapping("/{staffId}/roles/{roleCode}")
    public ResponseEntity<Result> revokeRole(@AuthenticationPrincipal StaffPrincipal actor,
                                             @PathVariable UUID staffId, @PathVariable String roleCode) {

        staffAccountService.revokeRole(actor, staffId, roleCode);
        return ResponseEntity.ok(new SuccessResult(StaffMessages.MEMBER_ROLE_REVOKED, HttpStatus.OK));
    }

    @Operation(summary = "Personel parolasını sıfırla",
            description = "Yönetici yeni parolayı atar, kilidi açar ve hesabı ilk girişte parola değiştirme "
                    + "bayrağına alır; açık token'lar geçersiz olur. E-posta akışı yoktur. Görev ayrılığı "
                    + "gereği aktör kendi hesabına uygulayamaz — son OWNER için docs/runbooks/staff-recovery.md.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Parola atandı"),
            @ApiResponse(responseCode = "404", description = "Personel bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Aktör kendi parolasını bu uçtan sıfırlayamaz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.STAFF_MANAGE, action = "staff.member.password.reset")
    @PostMapping("/{staffId}/password")
    public ResponseEntity<Result> resetPassword(@AuthenticationPrincipal StaffPrincipal actor,
                                                @PathVariable UUID staffId,
                                                @Valid @RequestBody ResetStaffPasswordDto request) {

        staffAccountService.resetPassword(actor, staffId, request.getPassword());
        return ResponseEntity.ok(new SuccessResult(StaffMessages.MEMBER_PASSWORD_RESET, HttpStatus.OK));
    }
}
