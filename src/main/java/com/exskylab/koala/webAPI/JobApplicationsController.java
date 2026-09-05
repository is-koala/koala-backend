package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.core.constants.JobApplicationMessages;
import com.exskylab.koala.core.dtos.jobApplication.request.AnswerApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.response.JobApplicationDetailDto;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Başvurular", description = "Başvuruya cevap verme.")
@RestController
@RequestMapping("/job-applications")
public class JobApplicationsController {

    private final JobApplicationService jobApplicationService;


    public JobApplicationsController(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }


    @Operation(summary = "Başvuruyu kabul et ya da reddet",
            description = "Yalnızca ilan sahibi ya da şirket yetkilisi cevaplayabilir. Kabul edildiğinde iş ataması oluşur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cevap kaydedildi"),
            @ApiResponse(responseCode = "404", description = "Başvuru bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Başvuru zaten cevaplanmış",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{applicationId}/respond")
    public ResponseEntity<SuccessDataResult<JobApplicationDetailDto>> respondToApplication(@PathVariable UUID applicationId,
                                                                                                                @RequestBody @Valid AnswerApplicationDto jobApplicationsApplicationIdPatchRequestDto){

        JobApplicationDetailDto dto = jobApplicationService.respondToApplication(applicationId, jobApplicationsApplicationIdPatchRequestDto);

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<JobApplicationDetailDto>(
                dto,
                JobApplicationMessages.JOB_APPLICATION_RESPONDED_SUCCESS,
                HttpStatus.OK
                )
        );

    }

    @Operation(summary = "Başvuruyu geri çek",
            description = "Yalnızca başvurunun sahibi geri çekebilir. Yalnızca APPLIED durumundan geçilebilir; kabul edilmiş başvurunun kendi iptal akışı vardır. Yetkisiz kullanıcı 404 alır, başvurunun varlığı sızmaz. Kayıt silinmez, durumu WITHDRAWN olur ve işverenin listesinde görünmeye devam eder.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Başvuru geri çekildi"),
            @ApiResponse(responseCode = "404", description = "Başvuru bulunamadı ya da yetki yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Başvuru APPLIED durumunda değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @PostMapping("/{applicationId}/withdraw")
    public ResponseEntity<SuccessDataResult<JobApplicationDetailDto>> withdrawApplication(@PathVariable UUID applicationId) {

        JobApplicationDetailDto dto = jobApplicationService.withdrawApplication(applicationId);

        return ResponseEntity.status(HttpStatus.OK).body(new SuccessDataResult<JobApplicationDetailDto>(
                dto,
                JobApplicationMessages.JOB_APPLICATION_WITHDRAWN_SUCCESS,
                HttpStatus.OK
                )
        );

    }
}
