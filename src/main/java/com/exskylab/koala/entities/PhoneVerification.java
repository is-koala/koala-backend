package com.exskylab.koala.entities;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@DiscriminatorValue("PHONE")
public class PhoneVerification extends UserVerification{


    @Column(name = "old_phone_number")
    private String oldPhoneNumber;

    @Column(name = "new_phone_number")
    private String newPhoneNumber;

    @Column(name = "token")
    private String token;

    @Column(name = "expires_at")
    private Instant expiresAt;

}
