package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.CompanyContactInvitationService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.dtos.company.request.CreateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.dtos.companyContact.request.InviteContactToCompanyDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.job.request.CompaniesCompanyIdJobsPostRequestDto;
import com.exskylab.koala.core.dtos.job.response.CompaniesCompanyIdJobsPostResponseDto;
import com.exskylab.koala.core.mappers.CompanyContactInvitationMapper;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.core.mappers.JobMapper;
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
public class CompanyController {

    private final CompanyService companyService;
    private final JobService jobService;
    private final CompanyContactInvitationService companyContactInvitationService;


    public CompanyController(CompanyService companyService, JobService jobService, CompanyContactInvitationService companyContactInvitationService) {
        this.companyService = companyService;
        this.jobService = jobService;
        this.companyContactInvitationService = companyContactInvitationService;
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
    public ResponseEntity<SuccessDataResult<CompaniesCompanyIdJobsPostResponseDto>> createJobForCompany(@PathVariable String companyId,
                                                                                                        @RequestBody @Valid CompaniesCompanyIdJobsPostRequestDto companiesCompanyIdJobsPostRequestDto){
        CompaniesCompanyIdJobsPostResponseDto response = jobService.createCorporateJob(companyId, companiesCompanyIdJobsPostRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<CompaniesCompanyIdJobsPostResponseDto>(response,
                        CompanyMessages.JOB_CREATED_SUCCESS,
                        HttpStatus.CREATED
                ));
    }

}
