package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CompanyContactInvitationService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.dtos.company.request.CreateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.request.UpdateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.companyContact.request.InviteContactToCompanyDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.job.request.CreateCompanyJobDto;
import com.exskylab.koala.core.dtos.job.response.CompanyJobDto;
import com.exskylab.koala.core.dtos.companyContact.response.CompanyContactDto;
import com.exskylab.koala.core.constants.GalleryMessages;
import com.exskylab.koala.core.dtos.gallery.request.UpdateGalleryDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.mappers.CompanyContactInvitationMapper;
import com.exskylab.koala.core.mappers.CompanyContactMapper;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.CompanyContactInvitation;
import com.exskylab.koala.entities.Job;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/companies")
public class CompaniesController {

    private final CompanyService companyService;
    private final CompanyContactMapper companyContactMapper;
    private final JobService jobService;
    private final CompanyContactInvitationService companyContactInvitationService;


    public CompaniesController(CompanyService companyService, JobService jobService,
                             CompanyContactInvitationService companyContactInvitationService,
                             CompanyContactMapper companyContactMapper) {
        this.companyService = companyService;
        this.jobService = jobService;
        this.companyContactInvitationService = companyContactInvitationService;
        this.companyContactMapper = companyContactMapper;
    }


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<CompanyDto>> addCompany(
            @RequestPart("company") @Valid CreateCompanyRequestDto createCompanyRequestDto,
            @RequestPart(value = "logo", required = false) MultipartFile logo){

        var response = companyService.addCompany(createCompanyRequestDto, logo);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<CompanyDto>(response, CompanyMessages.COMPANY_ADDED_SUCCESS, HttpStatus.CREATED)
        );

    }


    @PostMapping("/{companyId}/contact-invitations")
    public ResponseEntity<SuccessResult> sendCompanyContactInvitation(@PathVariable UUID companyId, @RequestBody @Valid InviteContactToCompanyDto contactToCompanyDto){

        companyService.inviteContactToCompany(companyId, contactToCompanyDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessResult(CompanyMessages.COMPANY_CONTACT_INVITATION_SENT_SUCCESS,
                        HttpStatus.CREATED));

    }

    @GetMapping("/{companyId}/contact-invitations")
    public ResponseEntity<SuccessDataResult<List<CompanyContactInvitationDto>>> getCompanyContactInvitations(@PathVariable UUID companyId){
        List<CompanyContactInvitationDto> response = companyContactInvitationService.getCompanyContactInvitationsByCompanyIdDto(companyId);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<List<CompanyContactInvitationDto>>(response,
                        CompanyMessages.COMPANY_CONTACT_INVITATIONS_LISTED_SUCCESS,
                        HttpStatus.OK

                ));


    }


    @PostMapping("/{companyId}/jobs")
    public ResponseEntity<SuccessDataResult<CompanyJobDto>> createJobForCompany(@PathVariable UUID companyId,
                                                                                                        @RequestBody @Valid CreateCompanyJobDto companiesCompanyIdJobsPostRequestDto){
        CompanyJobDto response = jobService.createCorporateJob(companyId, companiesCompanyIdJobsPostRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<CompanyJobDto>(response,
                        CompanyMessages.JOB_CREATED_SUCCESS,
                        HttpStatus.CREATED
                ));
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<SuccessDataResult<CompanyDto>> getCompany(@PathVariable UUID companyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.getCompanyDetail(companyId),
                CompanyMessages.COMPANY_FETCHED, HttpStatus.OK));
    }

    @PatchMapping("/{companyId}")
    public ResponseEntity<SuccessDataResult<CompanyDto>> updateCompany(
            @PathVariable UUID companyId, @RequestBody @Valid UpdateCompanyRequestDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.updateCompany(companyId, request),
                CompanyMessages.COMPANY_UPDATED, HttpStatus.OK));
    }

    @GetMapping("/{companyId}/contacts")
    public ResponseEntity<SuccessDataResult<List<CompanyContactDto>>> contacts(
            @PathVariable UUID companyId) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyContactMapper.toCompanyContactWithoutCompanyDtoList(companyService.getContacts(companyId)),
                CompanyMessages.CONTACTS_LISTED, HttpStatus.OK));
    }

    @PostMapping("/{companyId}/follow")
    public ResponseEntity<Result> followCompany(@PathVariable UUID companyId) {
        companyService.followCompany(companyId);
        return ResponseEntity.ok(new SuccessResult(CompanyMessages.COMPANY_FOLLOWED, HttpStatus.OK));
    }

    @DeleteMapping("/{companyId}/follow")
    public ResponseEntity<Result> unfollowCompany(@PathVariable UUID companyId) {
        companyService.unfollowCompany(companyId);
        return ResponseEntity.ok(new SuccessResult(CompanyMessages.COMPANY_UNFOLLOWED, HttpStatus.OK));
    }

    @GetMapping("/{companyId}/images")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> companyImages(@PathVariable UUID companyId) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.getCompanyGallery(companyId), GalleryMessages.IMAGE_LISTED, HttpStatus.OK));
    }

    @PostMapping(value = "/{companyId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> addCompanyImage(
            @PathVariable UUID companyId, @RequestPart("image") MultipartFile image) {

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                companyService.addCompanyImage(companyId, image),
                GalleryMessages.IMAGE_ADDED, HttpStatus.CREATED));
    }

    @PutMapping("/{companyId}/images")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> updateCompanyGallery(
            @PathVariable UUID companyId, @RequestBody @Valid UpdateGalleryDto request) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.replaceCompanyGallery(companyId, request),
                GalleryMessages.IMAGE_UPDATED, HttpStatus.OK));
    }

    @DeleteMapping("/{companyId}/images/{imageId}")
    public ResponseEntity<SuccessDataResult<List<GalleryImageDto>>> removeCompanyImage(
            @PathVariable UUID companyId, @PathVariable UUID imageId) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                companyService.removeCompanyImage(companyId, imageId),
                GalleryMessages.IMAGE_REMOVED, HttpStatus.OK));
    }

    @DeleteMapping("/{companyId}/contacts/{userId}")
    public ResponseEntity<Result> removeContact(@PathVariable UUID companyId, @PathVariable UUID userId) {
        companyService.removeContact(companyId, userId);
        return ResponseEntity.ok(new SuccessResult(CompanyMessages.CONTACT_REMOVED, HttpStatus.OK));
    }
}
