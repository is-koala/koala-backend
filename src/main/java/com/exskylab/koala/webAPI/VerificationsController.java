package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.UserVerificationService;
import com.exskylab.koala.core.constants.UserVerificationMessages;
import com.exskylab.koala.core.dtos.userVerification.request.VerificationRequestDto;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/verifications")
public class VerificationsController {


    private final UserVerificationService userVerificationService;

    public VerificationsController(UserVerificationService userVerificationService) {
        this.userVerificationService = userVerificationService;
    }


    @PostMapping("/{verificationId}/confirm")
    public ResponseEntity<SuccessResult> verify(@PathVariable("verificationId") UUID id, @RequestBody @Valid VerificationRequestDto verificationRequestDto){

        userVerificationService.verify(id, verificationRequestDto);

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessResult(
                UserVerificationMessages.VERIFICATION_SUCCESS,
                HttpStatus.OK
        ));

    }


}
