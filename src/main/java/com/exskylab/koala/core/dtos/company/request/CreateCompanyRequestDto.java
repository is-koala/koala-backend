package com.exskylab.koala.core.dtos.company.request;

import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.customValidations.ValidEnum;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.entities.CompanyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateCompanyRequestDto {

    @NotBlank(message = CompanyMessages.COMPANY_NAME_NOT_BLANK)
    private String name;

    @Email(message = CompanyMessages.INVALID_EMAIL)
    @NotBlank(message = CompanyMessages.COMPANY_EMAIL_NOT_BLANK)
    private String email;

    @NotNull(message = CompanyMessages.COMPANY_ADDRESS_NOT_NULL)
    @Valid
    private AddressWriteDto address;

    @NotBlank(message = CompanyMessages.COMPANY_PHONE_NUMBER_NOT_BLANK)
    private String phoneNumber;

    private String website;

    private String description;

    @NotBlank(message = CompanyMessages.COMPANY_TAX_NUMBER_NOT_BLANK)
    private String taxNumber;

    @NotNull(message = CompanyMessages.COMPANY_TYPE_NOT_BLANK)
    @ValidEnum(enumClass = CompanyType.class, message = CompanyMessages.INVALID_COMPANY_TYPE)
    private String type;

}
