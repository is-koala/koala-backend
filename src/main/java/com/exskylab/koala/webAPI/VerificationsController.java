package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.UserVerificationService;
import com.exskylab.koala.core.constants.UserVerificationMessages;
import com.exskylab.koala.core.dtos.userVerification.request.VerificationRequestDto;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Doğrulamalar", description = "E-posta ve telefon doğrulama kodlarının onaylanması.")
@RestController
@RequestMapping("/verifications")
public class VerificationsController {


    private final UserVerificationService userVerificationService;

    public VerificationsController(UserVerificationService userVerificationService) {
        this.userVerificationService = userVerificationService;
    }


    @Operation(summary = "Doğrulama kodunu onayla",
            description = "Kod doğrulanana kadar yeni e-posta ya da telefon numarası uygulanmaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doğrulama tamamlandı"),
            @ApiResponse(responseCode = "400", description = "Kod hatalı ya da süresi dolmuş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Doğrulama kaydı bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{verificationId}/confirm")
    public ResponseEntity<SuccessResult> verify(@PathVariable("verificationId") UUID id, @RequestBody @Valid VerificationRequestDto verificationRequestDto){

        userVerificationService.verify(id, verificationRequestDto);

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessResult(
                UserVerificationMessages.VERIFICATION_SUCCESS,
                HttpStatus.OK
        ));

    }


}
