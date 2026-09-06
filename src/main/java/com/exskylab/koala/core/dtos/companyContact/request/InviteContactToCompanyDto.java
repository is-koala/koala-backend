package com.exskylab.koala.core.dtos.companyContact.request;


import com.exskylab.koala.core.constants.CompanyContactMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InviteContactToCompanyDto {

    @NotBlank(message = CompanyContactMessages.TC_IDENTITY_NUMBER_NOT_BLANK)
    private String tcIdentityNumber;

    @NotBlank(message = CompanyContactMessages.EMAIL_NOT_BLANK)
    @Email(message = CompanyContactMessages.EMAIL_INVALID)
    private String email;

    @NotBlank(message = CompanyContactMessages.ROLE_NOT_BLANK)
    private String role;

}
