package com.exskylab.koala.core.dtos.user.request;

import com.exskylab.koala.core.constants.UserMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserMePatchRequestDto {

    //if user is verified by id number, they cannot change their first name
    private String firstName;

    //if user is verified by id number, they cannot change their last name
    private String lastName;

    private String phoneNumber;

    @Email(message = UserMessages.INVALID_EMAIL)
    private String email;

    //if user is verified by id number, they cannot change their birthdate
    private LocalDate birthdate;

    private String gender;

    private String about;

    private String qualifications;

    private String experiences;

    private String interests;

    private String iban;

}
