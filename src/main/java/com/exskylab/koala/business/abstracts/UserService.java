package com.exskylab.koala.business.abstracts;


import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.user.request.UpdateUserDto;
import com.exskylab.koala.core.dtos.user.request.ChangePasswordDto;
import com.exskylab.koala.core.dtos.user.request.UpdateProfileDto;
import com.exskylab.koala.core.dtos.user.request.VerifyIdentityDto;
import com.exskylab.koala.core.dtos.user.response.UserMeResponseDto;
import com.exskylab.koala.core.dtos.user.response.UserUpdateResponseDto;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

public interface UserService {

    User getByEmail(String email);
    User getById(UUID id);
    List<User> getAll();

    User save(User user);



    boolean existsByEmail(String email);
    boolean existsById(UUID id);



    UserUpdateResponseDto patchCurrentUser(UpdateProfileDto userMePatchRequestDto);

    void updateCurrentUserPassword(ChangePasswordDto userMeChangePasswordPutRequestDto, UUID currentSessionId);

    void updateProfilePicture(MultipartFile image);

    void updatePhoneVerification(User user, String newPhoneNumber);

    void updateEmailVerification(User user, String newEmail);


    String startIdentityChallenge();

    void verificateIdentityofAuthenticatedUser(VerifyIdentityDto usersMeIdentityVerificationRequestDto);

    UserMeResponseDto getCurrentUserFromDatabaseDto();

    AddressDto updateAddressForCurrentUser(@Valid AddressWriteDto addressDto);

    Page<User> search(String query, Pageable pageable);

    User getReference(UUID id);

    List<UserDao.RoleGrantView> getRoleGrants(UUID userId);

    List<UserDao.RoleGrantView> grantRole(StaffPrincipal actor, UUID userId, Role role);

    void revokeRole(StaffPrincipal actor, UUID userId, Role role);

    boolean bootstrapAdminRole(String email);
}
