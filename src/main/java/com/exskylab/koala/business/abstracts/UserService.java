package com.exskylab.koala.business.abstracts;


import com.exskylab.koala.core.dtos.address.request.CreateAddressRequestDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.user.UpdateUserDto;
import com.exskylab.koala.core.dtos.user.request.UserMeChangePasswordPutRequestDto;
import com.exskylab.koala.core.dtos.user.request.UserMePatchRequestDto;
import com.exskylab.koala.core.dtos.user.request.UsersMeIdentityVerificationRequestDto;
import com.exskylab.koala.core.dtos.user.response.UserMeResponseDto;
import com.exskylab.koala.core.dtos.user.response.UserUpdateResponseDto;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.User;
import jakarta.validation.Valid;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface UserService extends UserDetailsService {

    User getByEmail(String email);
    User getById(UUID id);
    List<User> getAll();

    User save(User user);



    boolean existsByEmail(String email);
    boolean existsById(UUID id);



    UserUpdateResponseDto patchCurrentUser(UserMePatchRequestDto userMePatchRequestDto);

    void updateCurrentUserPassword(UserMeChangePasswordPutRequestDto userMeChangePasswordPutRequestDto, UUID currentSessionId);

    void updateProfilePicture(MultipartFile image);

    void updatePhoneVerification(User user, String newPhoneNumber);

    void updateEmailVerification(User user, String newEmail);


    void verificateIdentityofAuthenticatedUser(UsersMeIdentityVerificationRequestDto usersMeIdentityVerificationRequestDto);

    UserMeResponseDto getCurrentUserFromDatabaseDto();

    AddressDto updateAddressForCurrentUser(@Valid CreateAddressRequestDto addressDto);
}
