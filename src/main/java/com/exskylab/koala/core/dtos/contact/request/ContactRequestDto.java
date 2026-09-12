package com.exskylab.koala.core.dtos.contact.request;

import com.exskylab.koala.core.constants.ContactMessages;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ContactRequestDto {

    @NotBlank(message = ContactMessages.TOPIC_REQUIRED)
    @Pattern(regexp = "destek|kvkk|sikayet|diger", message = ContactMessages.TOPIC_REQUIRED)
    private String topic;

    @NotBlank(message = ContactMessages.NAME_REQUIRED)
    @Size(max = 100, message = ContactMessages.NAME_TOO_LONG)
    private String name;

    @NotBlank(message = ContactMessages.EMAIL_REQUIRED)
    @Email(message = ContactMessages.EMAIL_INVALID)
    @Size(max = 254, message = ContactMessages.EMAIL_INVALID)
    private String email;

    @NotBlank(message = ContactMessages.MESSAGE_REQUIRED)
    @Size(max = 4000, message = ContactMessages.MESSAGE_TOO_LONG)
    private String message;

    @NotNull(message = ContactMessages.CONSENT_REQUIRED)
    @AssertTrue(message = ContactMessages.CONSENT_REQUIRED)
    private Boolean consent;

    /**
     * Gizli tuzak alan. İnsan boş bırakır; doluysa manager SMTP'ye gitmeden
     * aynı 200'ü döner — bota hangi alanın tuzak olduğunu öğretmemek için.
     */
    @Size(max = 200)
    private String website;
}
