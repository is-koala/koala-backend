package com.exskylab.koala.core.dtos.user.request;

import com.exskylab.koala.core.constants.UserMessages;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VerifyIdentityDto {

    @NotBlank(message = UserMessages.FIRST_NAME_CANNOT_BE_BLANK)
    private String firstName;

    @NotBlank(message = UserMessages.LAST_NAME_CANNOT_BE_BLANK)
    private String lastName;

    @NotNull(message = UserMessages.BIRTHDATE_CANNOT_BE_BLANK)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @NotBlank(message = UserMessages.TC_IDENTITY_NUMBER_CANNOT_BE_BLANK)
    @Pattern(regexp = "^[1-9]{1}[0-9]{9}[0,2,4,6,8]{1}$", message = UserMessages.INVALID_TC_IDENTITY_NUMBER)
    private String tcIdentityNumber;

    @NotBlank(message = UserMessages.TC_DOCUMENT_NUMBER_CANNOT_BE_BLANK)
    private String tcDocumentNumber;

}
