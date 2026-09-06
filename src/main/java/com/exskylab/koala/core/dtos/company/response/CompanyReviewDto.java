package com.exskylab.koala.core.dtos.company.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.entities.CompanyType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompanyReviewDto {

    private UUID id;

    private String name;

    private String email;

    private String phoneNumber;

    private String taxNumber;

    private CompanyType type;

    private String logoUrl;

    private CompanyApprovalStatus approvalStatus;

    private String approvalNote;

    private Instant approvalDecidedAt;

    private Instant createdAt;

    private SafeUserDto createdBy;

}
