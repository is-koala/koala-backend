package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.staff.request.ChangeStaffPasswordDto;
import com.exskylab.koala.core.dtos.staff.request.StaffLoginDto;
import com.exskylab.koala.core.dtos.staff.request.StaffRefreshDto;
import com.exskylab.koala.core.dtos.staff.response.StaffTokenDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Personel girişi", description = "Panelin kimlik doğrulama uçları.")
@RestController
@RequestMapping("/admin/auth")
public class StaffAuthController {

    private final StaffAccountService staffAccountService;

    public StaffAuthController(StaffAccountService staffAccountService) {
        this.staffAccountService = staffAccountService;
    }

    @Operation(summary = "Personel çıkışı",
            description = "Personelin bütün token'larını geçersiz kılar.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Çıkış yapıldı"))
    @PostMapping("/logout")
    public ResponseEntity<SuccessResult> logout(@AuthenticationPrincipal StaffPrincipal actor) {
        staffAccountService.logout(actor);
        return ResponseEntity.ok(new SuccessResult(StaffMessages.LOGOUT_SUCCESS, HttpStatus.OK));
    }

    @Operation(summary = "Kendi parolasını değiştirme",
            description = "İlk girişte parola değiştirme bayrağını da düşürür ve açık token'ları geçersiz kılar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Parola güncellendi"),
            @ApiResponse(responseCode = "401", description = "Mevcut parola hatalı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Yeni parola mevcut parolayla aynı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/password")
    public ResponseEntity<SuccessResult> changePassword(@AuthenticationPrincipal StaffPrincipal actor,
                                                        @Valid @RequestBody ChangeStaffPasswordDto request) {
        staffAccountService.changeOwnPassword(actor, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(new SuccessResult(StaffMessages.PASSWORD_CHANGED, HttpStatus.OK));
    }

    @Operation(summary = "Personel girişi",
            description = "E-posta ve parola ile Koala'nın kendi personel token'ını üretir. "
                    + "Yanlış parola ile olmayan hesap ayırt edilemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token üretildi"),
            @ApiResponse(responseCode = "401", description = "E-posta veya parola hatalı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla deneme",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<SuccessDataResult<StaffTokenDto>> login(@Valid @RequestBody StaffLoginDto request) {
        StaffTokenDto tokens = staffAccountService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(new SuccessDataResult<>(tokens, StaffMessages.LOGIN_SUCCESS, HttpStatus.OK));
    }

    @Operation(summary = "Personel token yenileme",
            description = "Yenileme token'ı ile yeni bir erişim token'ı üretir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token yenilendi"),
            @ApiResponse(responseCode = "401", description = "Yenileme token'ı geçersiz ya da oturum kapatılmış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<SuccessDataResult<StaffTokenDto>> refresh(@Valid @RequestBody StaffRefreshDto request) {
        StaffTokenDto tokens = staffAccountService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(new SuccessDataResult<>(tokens, StaffMessages.TOKEN_REFRESHED, HttpStatus.OK));
    }
}
