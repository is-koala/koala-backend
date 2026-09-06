package com.exskylab.koala.core.dtos.company.response;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.core.dtos.companyContact.response.CompanyContactDto;
import com.exskylab.koala.core.dtos.gallery.response.GalleryImageDto;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.entities.CompanyType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompanyDto {

    private UUID id;

    private String name;

    private String email;

    private String logoUrl;

    private String phoneNumber;

    private String website;

    private String description;

    private String taxNumber;

    private CompanyType type;

    private AddressDto address;

    private List<CompanyContactDto> companyContacts;

    private List<GalleryImageDto> images;

    private long followerCount;

    private boolean following;

    private boolean approved;

    private CompanyApprovalStatus approvalStatus;

    private String approvalNote;

    private Instant createdAt;

    private SafeUserDto createdBy;

}
