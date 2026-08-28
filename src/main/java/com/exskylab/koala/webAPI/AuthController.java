package com.exskylab.koala.webAPI;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.AuthService;
import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.dtos.auth.request.*;
import com.exskylab.koala.core.dtos.auth.response.AuthSetPasswordDto;
import com.exskylab.koala.core.dtos.auth.response.TokenResponseDto;
import com.exskylab.koala.core.helpers.BearerTokenExtractor;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@Tag(name = "Kimlik doğrulama", description = "Kayıt akışı, giriş, oturum yenileme ve şifre işlemleri.")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;


    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @SecurityRequirements
    @Operation(summary = "Kayıt akışını başlat",
            description = "E-posta adresine doğrulama bağlantısı gönderir. Adres kayıtlı olsa bile aynı 200 yanıtı döner: kayıtlı e-postaların açığa çıkmaması için. Aynı adrese ikinci istek yeniden gönderim sayılır, bekleyen kayıt silinip yenisi oluşturulur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adres kayıtlı değilse doğrulama e-postası gönderildi"),
            @ApiResponse(responseCode = "400", description = "E-posta biçimi geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla deneme yapıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/start-registration")
    public ResponseEntity<SuccessResult> startRegistration(@RequestBody @Valid AuthStartRegistrationDto authStartRegistrationDto){
        authService.startRegistration(authStartRegistrationDto);

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessResult(AuthMessages.START_REGISTRATION_SUCCESS,
                HttpStatus.OK));

    }

    @SecurityRequirements
    @Operation(summary = "Kayıt bağlantısındaki token doğrula",
            description = "E-postadaki bağlantıdan gelen token geçerliyse kaydın devamı için gereken bilgileri döndürür.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token geçerli"),
            @ApiResponse(responseCode = "400", description = "Token geçersiz ya da süresi dolmuş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla deneme yapıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/verify-registration-token")
    public ResponseEntity<SuccessDataResult<AuthSetPasswordDto>>
                            verifyRegistrationToken(@RequestBody @Valid AuthVerifyRegistrationTokenDto authVerifyRegistrationTokenDto){


        var response = authService.verifyRegistrationToken(authVerifyRegistrationTokenDto);
        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<>(response,
                AuthMessages.VERIFY_REGISTRATION_TOKEN_SUCCESS, HttpStatus.OK));



    }

    @SecurityRequirements
    @Operation(summary = "Kaydı tamamla",
            description = "Parola belirler ve zorunlu sözleşmeler için rıza kaydı üretir. Zorunlu sözleşmelerden biri onaylanmamışsa kayıt tamamlanmaz. isEmployer alanı zorunludur; eksik gönderilirse 400 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Kayıt tamamlandı, oturum açıldı"),
            @ApiResponse(responseCode = "400", description = "Zorunlu rıza eksik ya da alan doğrulaması başarısız",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla deneme yapıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/complete-registration")
    public ResponseEntity<SuccessDataResult<TokenResponseDto>> completeRegistration(@RequestBody @Valid AuthCompleteRegistrationDto authCompleteRegistrationDto,
                                                                                    HttpServletRequest request){


        var response = authService.completeRegistration(authCompleteRegistrationDto, ClientContext.from(request));


        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<>(
                        response,
                        AuthMessages.COMPLETE_REGISTRATION_SUCCESS,
                        HttpStatus.CREATED
            )
        );


    }

    @SecurityRequirements
    @Operation(summary = "Giriş yap",
            description = "Erişim ve yenileme token üretir. Cihaz X-Device-Id başlığıyla eşlenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Giriş başarılı"),
            @ApiResponse(responseCode = "401", description = "E-posta ya da parola hatalı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Çok fazla deneme yapıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<SuccessDataResult<TokenResponseDto>> login(@RequestBody @Valid AuthLoginDto authLoginDto, HttpServletRequest request){


        var response = authService.login(authLoginDto, ClientContext.from(request));

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<>(
                        response,
                        AuthMessages.LOGIN_SUCCESS,
                        HttpStatus.OK
                )
        );

    }


    @SecurityRequirements
    @Operation(summary = "Erişim token yenile",
            description = "Süresi dolmuş bir erişim token ile de çağrılabilir; yetkiyi veren şey yenileme token, oturumun aktifliği ve cihaz eşleşmesidir. Sahte imzalı token yine reddedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Yeni token çifti üretildi"),
            @ApiResponse(responseCode = "401", description = "Yenileme token geçersiz, oturum kapatılmış ya da cihaz eşleşmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<SuccessDataResult<TokenResponseDto>> refreshToken(@RequestBody @Valid RefreshTokenDto refreshTokenDto, HttpServletRequest request){
        UUID sessionId = jwtService.extractSessionIdAllowingExpired(BearerTokenExtractor.extract(request));

        var response = authService.refreshToken(refreshTokenDto, sessionId, ClientContext.from(request));

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<>(
                        response,
                        AuthMessages.REFRESH_TOKEN_SUCCESS,
                        HttpStatus.OK
                )
        );
    }


    @Operation(summary = "Oturumu kapat",
            description = "İstek atan oturumu sonlandırır; kullanıcının diğer cihazları etkilenmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturum kapatıldı")
    })
    @PostMapping("/logout")
    public ResponseEntity<SuccessResult> logout(HttpServletRequest request){
        UUID sessionId = jwtService.extractSessionId(BearerTokenExtractor.extract(request));

        authService.logout(sessionId);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(AuthMessages.LOGOUT_SUCCESS, HttpStatus.OK)
        );
    }



    @SecurityRequirements
    @Operation(summary = "Şifre sıfırlama bağlantısı iste",
            description = "Adres kayıtlı olmasa da aynı yanıt döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adres kayıtlıysa sıfırlama e-postası gönderildi"),
            @ApiResponse(responseCode = "429", description = "Çok fazla deneme yapıldı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<SuccessResult> forgotPassword(@RequestBody @Valid ForgotPasswordDto forgotPasswordDto){
        authService.requestPasswordReset(forgotPasswordDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(AuthMessages.PASSWORD_RESET_REQUESTED, HttpStatus.OK));
    }

    @SecurityRequirements
    @Operation(summary = "Şifreyi sıfırla",
            description = "E-postadaki token ile yeni parola belirler ve kullanıcının tüm oturumlarını kapatır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Parola güncellendi"),
            @ApiResponse(responseCode = "400", description = "Token geçersiz ya da süresi dolmuş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<SuccessResult> resetPassword(@RequestBody @Valid ResetPasswordDto resetPasswordDto){
        authService.resetPassword(resetPasswordDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(AuthMessages.PASSWORD_RESET_COMPLETED, HttpStatus.OK));
    }



}
