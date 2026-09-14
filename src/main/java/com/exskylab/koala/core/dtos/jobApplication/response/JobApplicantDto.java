package com.exskylab.koala.core.dtos.jobApplication.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobApplicantDto {

    private UUID id;

    private ApplicationStatus status;

    private Instant applicationDateTime;

    private String notes;

    private SafeUserDto applicant;

    private String cvUrl;

    private String jobTitle;

    @Schema(types = {"string", "null"}, format = "date-time",
            description = "Başvurunun kabul ya da reddedildiği an; henüz cevaplanmadıysa null.")
    private Instant answeredDateTime;

    @Schema(types = {"string", "null"}, format = "uuid",
            description = "Kabulle doğan atamanın kimliği; başvuru kabul edilmediyse null.")
    private UUID assignmentId;

    @Schema(types = {"string", "null"},
            description = "Ret gerekçesi; reddedilmediyse ya da gerekçe yazılmadıysa null.")
    private String rejectionReason;

}
