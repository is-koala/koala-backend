package com.exskylab.koala.core.dtos.company.request;

import com.exskylab.koala.core.constants.CompanyMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCompanyRequestDto {

    @Size(max = 255)
    private String name;

    @Email(message = CompanyMessages.INVALID_EMAIL)
    private String email;

    @Size(max = 32)
    private String phoneNumber;

    @Size(max = 512)
    private String website;

    @Size(max = 4000)
    private String description;
}
