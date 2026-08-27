package com.exskylab.koala.core.dtos.auth.request;

import com.exskylab.koala.core.constants.AuthMessages;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthStartRegistrationDto {

    @NotNull(message =AuthMessages.FIRST_NAME_NOT_NULL)
    private String firstName;

    @NotNull(message =AuthMessages.LAST_NAME_NOT_NULL)
    private String lastName;

    @NotNull(message =AuthMessages.EMAIL_NOT_NULL)
    @Email(message = AuthMessages.INVALID_EMAIL)
    private String email;

    @NotNull(message = AuthMessages.EMPLOYER_FLAG_NOT_NULL)
    @JsonProperty("isEmployer")
    private Boolean isEmployer;

}
