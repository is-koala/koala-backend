package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CompanyContactInvitationService;
import com.exskylab.koala.core.constants.CompanyContactInvitationMessages;
import com.exskylab.koala.core.dtos.companyContactInvitation.request.AnswerToContactInvitationDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.GetCompanyContactInvitationDto;
import com.exskylab.koala.core.mappers.CompanyContactInvitationMapper;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/contact-invitations")
public class CompanyContactInvitationsController {

    private final CompanyContactInvitationService companyContactInvitationService;
    private final CompanyContactInvitationMapper companyContactInvitationMapper;


    public CompanyContactInvitationsController(CompanyContactInvitationService companyContactInvitationService, CompanyContactInvitationMapper companyContactInvitationMapper) {
        this.companyContactInvitationService = companyContactInvitationService;
        this.companyContactInvitationMapper = companyContactInvitationMapper;
    }


    @PostMapping("/{invitationId}")
    public ResponseEntity<SuccessDataResult<GetCompanyContactInvitationDto>> answerToInvitation(@PathVariable UUID invitationId, @RequestBody @Valid AnswerToContactInvitationDto answerToContactInvitationDto){

        var result = companyContactInvitationService.answerToInvitation(invitationId, answerToContactInvitationDto.getAccepted());

        return ResponseEntity.status(HttpStatus.OK)
                .body(new SuccessDataResult<>(
                        result,
                        CompanyContactInvitationMessages.INVITATION_ANSWERED_SUCCESS,
                        HttpStatus.OK
                        ));

    }


    @GetMapping("/{invitationId}")
    public ResponseEntity<SuccessDataResult<GetCompanyContactInvitationDto>> getInvitation(@PathVariable UUID invitationId){
        var result = companyContactInvitationService.getInvitationById(invitationId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new SuccessDataResult<>(
                        result,
                        CompanyContactInvitationMessages.INVITATION_FETCHED_SUCCESS,
                        HttpStatus.OK
                ));


    }



}
