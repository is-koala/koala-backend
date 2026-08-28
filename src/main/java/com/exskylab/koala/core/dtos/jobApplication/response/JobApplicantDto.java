package com.exskylab.koala.core.dtos.jobApplication.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

}
