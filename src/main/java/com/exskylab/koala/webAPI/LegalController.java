package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.core.dtos.legal.request.ConsentDecisionDto;
import com.exskylab.koala.core.dtos.legal.request.DeleteAccountRequestDto;
import com.exskylab.koala.core.dtos.legal.response.AgreementVersionDto;
import com.exskylab.koala.core.dtos.legal.response.ConsentRecordDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.mappers.LegalMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/legal")
public class LegalController {

    private final LegalService legalService;
    private final LegalMapper legalMapper;
    private final SecurityService securityService;

    public LegalController(LegalService legalService, LegalMapper legalMapper,
                           SecurityService securityService) {
        this.legalService = legalService;
        this.legalMapper = legalMapper;
        this.securityService = securityService;
    }

    @GetMapping("/agreements")
    public ResponseEntity<SuccessDataResult<List<AgreementVersionDto>>> currentAgreements() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toAgreementVersionDtoList(legalService.getCurrentAgreements()),
                LegalMessages.AGREEMENTS_LISTED, HttpStatus.OK));
    }

    @GetMapping("/consents/me")
    public ResponseEntity<SuccessDataResult<List<ConsentRecordDto>>> myConsents() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toConsentRecordDtoList(
                        legalService.getConsentsOfUser(securityService.getAuthenticatedUserId())),
                LegalMessages.CONSENTS_LISTED, HttpStatus.OK));
    }

    @PostMapping("/consents")
    public ResponseEntity<SuccessDataResult<List<ConsentRecordDto>>> recordConsents(
            @RequestBody @Valid List<ConsentDecisionDto> decisions, HttpServletRequest request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                legalMapper.toConsentRecordDtoList(legalService.recordConsents(
                        securityService.getAuthenticatedUser(), decisions, ClientContext.from(request))),
                LegalMessages.CONSENTS_RECORDED, HttpStatus.OK));
    }

    @PostMapping("/account-deletion")
    public ResponseEntity<SuccessResult> requestAccountDeletion(
            @RequestBody @Valid DeleteAccountRequestDto request) {

        legalService.requestAccountDeletion(securityService.getAuthenticatedUser(), request.getReason());

        return ResponseEntity.ok(new SuccessResult(LegalMessages.DELETION_REQUESTED, HttpStatus.OK));
    }
}
