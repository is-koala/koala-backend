package com.exskylab.koala.webAPI;

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

@RestController
@RequestMapping("/job-applications")
public class JobApplicationsController {

    private final JobApplicationService jobApplicationService;


    public JobApplicationsController(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }


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
}
