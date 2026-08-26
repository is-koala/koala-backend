package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.dtos.address.request.CreateAddressRequestDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.job.request.UsersMeJobsPostRequestDto;
import com.exskylab.koala.core.dtos.job.response.UsersMeJobsPostResponseDto;
import com.exskylab.koala.core.dtos.user.request.UserMeChangePasswordPutRequestDto;
import com.exskylab.koala.core.dtos.user.request.UserMePatchRequestDto;
import com.exskylab.koala.core.dtos.user.request.UsersMeIdentityVerificationRequestDto;
import com.exskylab.koala.core.dtos.user.response.UserMeResponseDto;
import com.exskylab.koala.core.dtos.user.response.UserUpdateResponseDto;
import com.exskylab.koala.core.helpers.BearerTokenExtractor;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.User;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;
    private final SecurityService securityService;
    private final JobService jobService;
    private final JobMapper jobMapper;

    public UserController(UserService userService, JwtService jwtService, SecurityService securityService, JobService jobService, JobMapper jobMapper) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.securityService = securityService;
        this.jobService = jobService;
        this.jobMapper = jobMapper;
    }

   @GetMapping("/me")
    public ResponseEntity<SuccessDataResult<UserMeResponseDto>> getCurrentUser(){

       UserMeResponseDto currentUserDto = userService.getCurrentUserFromDatabaseDto();

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<UserMeResponseDto>(
                        currentUserDto,
                        UserMessages.USER_RETRIEVED_SUCCESSFULLY,
                        HttpStatus.OK

                ));
    }


    @PatchMapping("/me")
    public ResponseEntity<SuccessDataResult<UserUpdateResponseDto>> patchCurrentUser(@RequestBody @Valid UserMePatchRequestDto userMePatchRequestDto){

        UserUpdateResponseDto patchedUser = userService.patchCurrentUser(userMePatchRequestDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<UserUpdateResponseDto>(
                        patchedUser,
                        UserMessages.USER_UPDATED_SUCCESSFULLY,
                        HttpStatus.OK));
    }


    @PutMapping("/me/password")
    public ResponseEntity<SuccessResult> updateCurrentUserPassword(@RequestBody @Valid UserMeChangePasswordPutRequestDto userMeChangePasswordPutRequestDto,
                                                                   HttpServletRequest request){
        UUID currentSessionId = jwtService.extractSessionId(BearerTokenExtractor.extract(request));

        userService.updateCurrentUserPassword(userMeChangePasswordPutRequestDto, currentSessionId);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(
                        UserMessages.USER_PASSWORD_UPDATED_SUCCESSFULLY,
                        HttpStatus.OK));
    }

    @PutMapping(value = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResult> uploadProfilePicture(@Parameter(
            description = "Profile picture",
            required = true,
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
                                                                  @RequestPart("image") MultipartFile image){

        userService.updateProfilePicture(image);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(
                        UserMessages.USER_PROFILE_PICTURE_UPLOADED_SUCCESSFULLY,
                        HttpStatus.OK));
    }

    @PutMapping("/me/identity-verification")
    public ResponseEntity<SuccessResult> verifyIdentity(@RequestBody @Valid UsersMeIdentityVerificationRequestDto usersMeIdentityVerificationRequestDto){

        userService.verificateIdentityofAuthenticatedUser(usersMeIdentityVerificationRequestDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessResult(
                        UserMessages.USER_IDENTITY_VERIFICATED_SUCCESSFULLY,
                        HttpStatus.OK));
    }

    @PutMapping("/me/address")
    public ResponseEntity<SuccessDataResult<AddressDto>> updateAddressForCurrentUser(@RequestBody @Valid CreateAddressRequestDto addressDto){

        AddressDto updatedAddress = userService.updateAddressForCurrentUser(addressDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                new SuccessDataResult<AddressDto>(
                        updatedAddress,
                        UserMessages.USER_ADDRESS_UPDATED_SUCCESSFULLY,
                        HttpStatus.OK));
    }


    @PostMapping("/me/jobs")
    public ResponseEntity<SuccessDataResult<UsersMeJobsPostResponseDto>> createJobForCurrentUser(@RequestBody @Valid UsersMeJobsPostRequestDto usersMeJobsPostRequestDto){

        UsersMeJobsPostResponseDto createdJob = jobService.createIndividualJob(usersMeJobsPostRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SuccessDataResult<UsersMeJobsPostResponseDto>(
                        createdJob,
                        UserMessages.JOB_CREATED_SUCCESSFULLY,
                        HttpStatus.CREATED
                ));


    }


}
