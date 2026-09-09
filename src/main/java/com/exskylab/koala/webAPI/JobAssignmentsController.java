package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.dtos.jobAssignment.request.AnswerAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.PaymentSessionDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.JobAssignmentDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.PaymentSessionResponseDto;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "İş atamaları", description = "Atama okuma, cevap verme ve ödeme oturumu.")
@RestController
@RequestMapping("/job-assignments")
public class JobAssignmentsController {

    private final JobAssignmentService jobAssignmentService;


    public JobAssignmentsController(JobAssignmentService jobAssignmentService) {
        this.jobAssignmentService = jobAssignmentService;
    }


    @Operation(summary = "Atamayı getir",
            description = "Yalnızca atanan işçi, ilan sahibi veya şirket ilanının güncel yetkilisi okuyabilir. Taraf olmayan kullanıcı 404 alır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atama getirildi"),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/{assignmentId}")
    public ResponseEntity<SuccessDataResult<MyAssignmentDto>> getAssignment(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAssignmentService.getMyAssignment(assignmentId),
                JobAssignmentMessages.RETRIEVED,
                HttpStatus.OK));
    }




    @Operation(summary = "Atamayı kabul et ya da reddet",
            description = "accepted alanı Boolean ve zorunludur; eksik gönderilirse 400 döner. Primitive olsaydı eksik alan sessizce reddetme sayılırdı.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cevap kaydedildi"),
            @ApiResponse(responseCode = "400", description = "accepted alanı eksik",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da taraf değilsiniz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Atama bu durumdan cevaplanamaz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/respond")
    public ResponseEntity<SuccessDataResult<JobAssignmentDto>> answerToAssignment(@PathVariable UUID assignmentId,
            @RequestBody @Valid AnswerAssignmentDto jobAssignmentsAssignmentIdStatusPatchRequestDto){

        JobAssignmentDto response = jobAssignmentService.answerToAssignment(assignmentId, jobAssignmentsAssignmentIdStatusPatchRequestDto);

        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        new SuccessDataResult<>(
                                response,
                                JobAssignmentMessages.ANSWER_SUCCESS,
                                HttpStatus.OK
                        )
                );
    }


    @Operation(summary = "Ödeme oturumu başlat",
            description = "İşveren için Iyzico ödeme formu oturumu açar. Adres eksikliği ya da sağlayıcı hatası durumunda ödeme denemesi de geri alınır, sahipsiz kayıt kalmaz. Her deneme ayrı bir Payment kaydıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ödeme oturumu açıldı"),
            @ApiResponse(responseCode = "400", description = "Fatura adresi eksik",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Atama bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Bu atamanın parası zaten tahsil edilmiş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "502", description = "Ödeme sağlayıcısına ulaşılamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{assignmentId}/payment-session")
    public ResponseEntity<SuccessDataResult<PaymentSessionDto>> initiatePaymentSession(@PathVariable UUID assignmentId, HttpServletRequest request){
        PaymentSessionDto dto = jobAssignmentService.initiatePaymentSession(assignmentId, getClientIpAddress(request));

        return ResponseEntity.status(HttpStatus.OK)
                .body(new SuccessDataResult<>(
                        dto,
                        JobAssignmentMessages.PAYMENT_SESSION_INITIATED,
                        HttpStatus.OK
                ));
    }



    private String getClientIpAddress(HttpServletRequest request) {
        String remoteAddr = "";
        if(request != null){
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)){
                remoteAddr = request.getRemoteAddr();
            }
        }

        return  remoteAddr;

    }


}
