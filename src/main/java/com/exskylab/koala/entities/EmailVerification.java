package com.exskylab.koala.entities;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@DiscriminatorValue("EMAIL")
public class EmailVerification extends UserVerification {

    @Column(name = "old_email")
    private String oldEmail;

    @Column(name = "new_email")
    private String newEmail;

    @Column(name = "token")
    private String token;

    @Column(name = "expires_at")
    private Instant expiresAt;

}