package com.exskylab.koala.core.dtos.companyContact.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.CompanyContactRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompanyContactDto {

    private UUID id;

    private SafeUserDto user;

    private CompanyContactRole role;

}
