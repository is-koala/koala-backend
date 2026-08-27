package com.exskylab.koala.core.dtos.user.response;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.company.response.CompanyMembershipDto;
import com.exskylab.koala.entities.Role;

import java.util.List;
import java.util.Set;
import com.exskylab.koala.entities.Gender;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserMeResponseDto {

    private UUID id;

    private String firstName;

    private String lastName;

    private String email;

    private String profilePictureUrl;

    private String phoneNumber;

    private LocalDate birthDate;

    private Gender gender;

    private String tcIdentityNumber;

    private String about;

    private String qualifications;

    private String interests;

    private AddressDto address;

    private boolean employer;

    private boolean emailVerified;

    private boolean phoneVerified;

    private boolean identityVerified;

    private Set<Role> roles;

    private List<CompanyMembershipDto> companies;


}
