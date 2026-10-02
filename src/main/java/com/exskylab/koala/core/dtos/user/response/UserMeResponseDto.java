package com.exskylab.koala.core.dtos.user.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.company.response.CompanyMembershipDto;
import com.exskylab.koala.core.dtos.badge.response.BadgeDto;
import com.exskylab.koala.core.dtos.education.response.EducationDto;
import com.exskylab.koala.core.dtos.experience.response.ExperienceDto;
import com.exskylab.koala.entities.Role;

import java.util.List;
import java.util.Set;
import com.exskylab.koala.entities.Gender;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    private String maskedTcIdentityNumber;

    private String jobTitle;

    private String about;

    private String qualifications;

    private List<ExperienceDto> experiences;

    private String interests;

    private String maskedIban;

    private String cvUrl;

    private List<EducationDto> educations;

    private List<BadgeDto> badges;

    @Schema(description = "Hakkındaki değerlendirmelerin ortalaması; değerlendirme yoksa 0. GET /users/{id} (PublicProfileDto) "
            + "ve GET /users/{id}/rating-summary ile aynı kaynaktan. reviewCount 0 iken istemci puan çizmez.")
    private BigDecimal averageRating;

    @Schema(description = "Hakkındaki değerlendirme sayısı; yoksa 0. GET /users/{id} (PublicProfileDto) ile aynı kaynaktan.")
    private int reviewCount;

    private AddressDto address;

    @JsonProperty("isEmployer")
    private boolean employer;

    private boolean emailVerified;

    private boolean phoneVerified;

    private boolean identityVerified;

    private Set<Role> roles;

    private List<CompanyMembershipDto> companies;


}
