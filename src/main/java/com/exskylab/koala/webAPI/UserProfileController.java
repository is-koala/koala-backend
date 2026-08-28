package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CvService;
import com.exskylab.koala.business.abstracts.EducationService;
import com.exskylab.koala.business.abstracts.SavedJobService;
import com.exskylab.koala.core.constants.CvMessages;
import com.exskylab.koala.core.constants.EducationMessages;
import com.exskylab.koala.core.constants.SavedJobMessages;
import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.core.dtos.education.response.EducationDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.mappers.EducationMapper;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.companyFollow.response.FollowedCompanyDto;
import com.exskylab.koala.core.mappers.JobMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/me")
public class UserProfileController {

    private final CompanyService companyService;
    private final SavedJobService savedJobService;
    private final CvService cvService;
    private final EducationService educationService;
    private final JobMapper jobMapper;
    private final EducationMapper educationMapper;
    private final MediaUrlResolver mediaUrlResolver;

    public UserProfileController(CompanyService companyService,
                                 SavedJobService savedJobService, CvService cvService,
                                 EducationService educationService, JobMapper jobMapper,
                                 EducationMapper educationMapper, MediaUrlResolver mediaUrlResolver) {
        this.companyService = companyService;
        this.savedJobService = savedJobService;
        this.cvService = cvService;
        this.educationService = educationService;
        this.jobMapper = jobMapper;
        this.educationMapper = educationMapper;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @GetMapping("/followed-companies")
    public ResponseEntity<SuccessDataResult<PageDto<FollowedCompanyDto>>> followedCompanies(
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.getFollowedCompanies(pageable),
                CompanyMessages.FOLLOWED_COMPANIES_LISTED, HttpStatus.OK));
    }

    @GetMapping("/saved-jobs")
    public ResponseEntity<SuccessDataResult<List<JobSummaryDto>>> savedJobs() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                jobMapper.toJobSummaryDtoList(savedJobService.getMySavedJobs()),
                SavedJobMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/saved-jobs/{jobId}")
    public ResponseEntity<Result> saveJob(@PathVariable UUID jobId) {
        savedJobService.saveJob(jobId);
        return ResponseEntity.ok(new SuccessResult(SavedJobMessages.SAVED, HttpStatus.OK));
    }

    @DeleteMapping("/saved-jobs/{jobId}")
    public ResponseEntity<Result> removeSavedJob(@PathVariable UUID jobId) {
        savedJobService.removeSavedJob(jobId);
        return ResponseEntity.ok(new SuccessResult(SavedJobMessages.REMOVED, HttpStatus.OK));
    }

    @PutMapping("/cv")
    public ResponseEntity<SuccessDataResult<String>> uploadCv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                mediaUrlResolver.toFileUrl(cvService.uploadCv(file)),
                CvMessages.UPLOADED, HttpStatus.OK));
    }

    @DeleteMapping("/cv")
    public ResponseEntity<Result> deleteCv() {
        cvService.deleteCv();
        return ResponseEntity.ok(new SuccessResult(CvMessages.DELETED, HttpStatus.OK));
    }

    @GetMapping("/educations")
    public ResponseEntity<SuccessDataResult<List<EducationDto>>> educations() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toEducationDtoList(educationService.getMyEducations()),
                EducationMessages.LISTED, HttpStatus.OK));
    }

    @PostMapping("/educations")
    public ResponseEntity<SuccessDataResult<EducationDto>> addEducation(
            @RequestBody @Valid SaveEducationDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                educationMapper.toEducationDto(educationService.addEducation(request)),
                EducationMessages.CREATED, HttpStatus.CREATED));
    }

    @PutMapping("/educations/{educationId}")
    public ResponseEntity<SuccessDataResult<EducationDto>> updateEducation(
            @PathVariable UUID educationId, @RequestBody @Valid SaveEducationDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                educationMapper.toEducationDto(educationService.updateEducation(educationId, request)),
                EducationMessages.UPDATED, HttpStatus.OK));
    }

    @DeleteMapping("/educations/{educationId}")
    public ResponseEntity<Result> deleteEducation(@PathVariable UUID educationId) {
        educationService.deleteEducation(educationId);
        return ResponseEntity.ok(new SuccessResult(EducationMessages.DELETED, HttpStatus.OK));
    }
}
