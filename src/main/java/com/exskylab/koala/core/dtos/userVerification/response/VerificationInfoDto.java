package com.exskylab.koala.core.dtos.userVerification.response;

import com.exskylab.koala.entities.EmailVerification;
import com.exskylab.koala.entities.PhoneVerification;
import com.exskylab.koala.entities.UserVerification;
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
public class VerificationInfoDto {

    private UUID id;

    private String type;

    private String sentTo;

    private Instant expiresAt;


    public VerificationInfoDto(UserVerification verification) {
        this.id = verification.getId();
        if (verification instanceof EmailVerification emailVerification) {
            this.type = "EMAIL";
            this.sentTo = emailVerification.getNewEmail();
            this.expiresAt = emailVerification.getExpiresAt();
        }else if (verification instanceof PhoneVerification phoneVerification){
            this.type = "PHONE";
            this.sentTo = phoneVerification.getNewPhoneNumber();
            this.expiresAt = phoneVerification.getExpiresAt();
        }
    }

}
