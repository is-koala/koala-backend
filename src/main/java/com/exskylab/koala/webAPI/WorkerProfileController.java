package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.AvailabilityService;
import com.exskylab.koala.business.abstracts.ExperienceService;
import com.exskylab.koala.business.abstracts.JobAlertService;
import com.exskylab.koala.core.constants.AvailabilityMessages;
import com.exskylab.koala.core.constants.ExperienceMessages;
import com.exskylab.koala.core.constants.JobAlertMessages;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityDto;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityExceptionDto;
import com.exskylab.koala.core.dtos.availability.response.AvailabilityDto;
import com.exskylab.koala.core.dtos.availability.response.AvailabilityExceptionDto;
import com.exskylab.koala.core.dtos.experience.request.SaveExperienceDto;
import com.exskylab.koala.core.dtos.experience.response.ExperienceDto;
import com.exskylab.koala.core.dtos.jobAlert.request.SaveJobAlertDto;
import com.exskylab.koala.core.dtos.jobAlert.response.JobAlertDto;
import com.exskylab.koala.core.mappers.AvailabilityMapper;
import com.exskylab.koala.core.mappers.ExperienceMapper;
import com.exskylab.koala.core.mappers.JobAlertMapper;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class WorkerProfileController {

    private final ExperienceService experienceService;
    private final AvailabilityService availabilityService;
    private final JobAlertService jobAlertService;
    private final ExperienceMapper experienceMapper;
    private final AvailabilityMapper availabilityMapper;
    private final JobAlertMapper jobAlertMapper;

    public WorkerProfileController(ExperienceService experienceService,
                                   AvailabilityService availabilityService,
                                   JobAlertService jobAlertService,
                                   ExperienceMapper experienceMapper,
                                   AvailabilityMapper availabilityMapper,
                                   JobAlertMapper jobAlertMapper) {
        this.experienceService = experienceService;
        this.availabilityService = availabilityService;
        this.jobAlertService = jobAlertService;
        this.experienceMapper = experienceMapper;
        this.availabilityMapper = availabilityMapper;
        this.jobAlertMapper = jobAlertMapper;
    }

    @GetMapping("/users/me/experiences")
    public ResponseEntity<SuccessDataResult<List<ExperienceDto>>> myExperiences() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                experienceMapper.toExperienceDtoList(experienceService.getMyExperiences()),
                ExperienceMessages.LISTED, HttpStatus.OK));
    }

    @GetMapping("/users/{userId}/experiences")
    public ResponseEntity<SuccessDataResult<List<ExperienceDto>>> experiencesOf(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                experienceMapper.toExperienceDtoList(experienceService.getExperiencesOf(userId)),
                ExperienceMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/users/me/experiences")
    public ResponseEntity<SuccessDataResult<ExperienceDto>> addExperience(
            @RequestBody @Valid SaveExperienceDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                experienceMapper.toExperienceDto(experienceService.addExperience(request)),
                ExperienceMessages.CREATED, HttpStatus.CREATED));
    }

    @PutMapping("/users/me/experiences/{experienceId}")
    public ResponseEntity<SuccessDataResult<ExperienceDto>> updateExperience(
            @PathVariable UUID experienceId, @RequestBody @Valid SaveExperienceDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                experienceMapper.toExperienceDto(experienceService.updateExperience(experienceId, request)),
                ExperienceMessages.UPDATED, HttpStatus.OK));
    }

    @DeleteMapping("/users/me/experiences/{experienceId}")
    public ResponseEntity<Result> deleteExperience(@PathVariable UUID experienceId) {
        experienceService.deleteExperience(experienceId);
        return ResponseEntity.ok(new SuccessResult(ExperienceMessages.DELETED, HttpStatus.OK));
    }

    @GetMapping("/users/{userId}/availability")
    public ResponseEntity<SuccessDataResult<AvailabilityDto>> availabilityOf(@PathVariable UUID userId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                new AvailabilityDto(
                        availabilityMapper.toSlotDtoList(availabilityService.getSlotsOf(userId)),
                        availabilityMapper.toExceptionDtoList(availabilityService.getUpcomingExceptionsOf(userId))),
                AvailabilityMessages.FETCHED, HttpStatus.OK));
    }

    @PutMapping("/users/me/availability")
    public ResponseEntity<SuccessDataResult<AvailabilityDto>> replaceAvailability(
            @RequestBody @Valid SaveAvailabilityDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                new AvailabilityDto(
                        availabilityMapper.toSlotDtoList(availabilityService.replaceMySlots(request)),
                        List.of()),
                AvailabilityMessages.SAVED, HttpStatus.OK));
    }

    @PutMapping("/users/me/availability/exceptions")
    public ResponseEntity<SuccessDataResult<AvailabilityExceptionDto>> saveAvailabilityException(
            @RequestBody @Valid SaveAvailabilityExceptionDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                availabilityMapper.toExceptionDto(availabilityService.saveMyException(request)),
                AvailabilityMessages.EXCEPTION_SAVED, HttpStatus.OK));
    }

    @DeleteMapping("/users/me/availability/exceptions/{exceptionId}")
    public ResponseEntity<Result> deleteAvailabilityException(@PathVariable UUID exceptionId) {
        availabilityService.deleteMyException(exceptionId);
        return ResponseEntity.ok(new SuccessResult(AvailabilityMessages.EXCEPTION_DELETED, HttpStatus.OK));
    }

    @GetMapping("/users/me/job-alerts")
    public ResponseEntity<SuccessDataResult<List<JobAlertDto>>> myJobAlerts() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAlertMapper.toJobAlertDtoList(jobAlertService.getMyAlerts()),
                JobAlertMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/users/me/job-alerts")
    public ResponseEntity<SuccessDataResult<JobAlertDto>> createJobAlert(
            @RequestBody @Valid SaveJobAlertDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                jobAlertMapper.toJobAlertDto(jobAlertService.createAlert(request)),
                JobAlertMessages.CREATED, HttpStatus.CREATED));
    }

    @PutMapping("/users/me/job-alerts/{alertId}")
    public ResponseEntity<SuccessDataResult<JobAlertDto>> updateJobAlert(
            @PathVariable UUID alertId, @RequestBody @Valid SaveJobAlertDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAlertMapper.toJobAlertDto(jobAlertService.updateAlert(alertId, request)),
                JobAlertMessages.UPDATED, HttpStatus.OK));
    }

    @DeleteMapping("/users/me/job-alerts/{alertId}")
    public ResponseEntity<Result> deleteJobAlert(@PathVariable UUID alertId) {
        jobAlertService.deleteAlert(alertId);
        return ResponseEntity.ok(new SuccessResult(JobAlertMessages.DELETED, HttpStatus.OK));
    }
}
