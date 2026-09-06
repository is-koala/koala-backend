package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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

@Tag(name = "Şirket davetleri", description = "Yetkili davetlerinin görüntülenmesi ve cevaplanması.")
@RestController
@RequestMapping("/contact-invitations")
public class CompanyContactInvitationsController {

    private final CompanyContactInvitationService companyContactInvitationService;
    private final CompanyContactInvitationMapper companyContactInvitationMapper;


    public CompanyContactInvitationsController(CompanyContactInvitationService companyContactInvitationService, CompanyContactInvitationMapper companyContactInvitationMapper) {
        this.companyContactInvitationService = companyContactInvitationService;
        this.companyContactInvitationMapper = companyContactInvitationMapper;
    }


    @Operation(summary = "Daveti kabul et ya da reddet",
            description = "Yalnızca davet edilen cevaplayabilir; hem davet edilene hem davet edene bildirim gider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Davet cevaplandı"),
            @ApiResponse(responseCode = "404", description = "Davet bulunamadı ya da size ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Davet zaten cevaplanmış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
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


    @Operation(summary = "Davet detayı",
            description = "Yalnızca davet edilen, daveti gönderen ve şirketin ADMIN yetkilisi okuyabilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Davet döndürüldü"),
            @ApiResponse(responseCode = "404", description = "Davet bulunamadı ya da size ait değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
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
