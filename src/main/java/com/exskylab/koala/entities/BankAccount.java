package com.exskylab.koala.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SQLDelete(sql = "UPDATE bank_accounts SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "bank_accounts")
public class BankAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "iban", nullable = false, length = 34)
    private String iban;

    @Column(name = "account_holder_name", length = 255)
    private String accountHolderName;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryAccount;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    private BankAccountVerificationStatus verificationStatus = BankAccountVerificationStatus.UNVERIFIED;

    @Column(name = "verification_reference", length = 32)
    private String verificationReference;

    @Column(name = "verification_amount", precision = 19, scale = 2)
    private BigDecimal verificationAmount;

    @Column(name = "verification_started_at")
    private Instant verificationStartedAt;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts;

    @Column(name = "account_holder_name_verified", nullable = false)
    private boolean accountHolderNameVerified;

    public void setIban(String iban) {
        if (!Objects.equals(this.iban, iban)) {
            forgetVerification();
        }
        this.iban = iban;
    }

    public void forgetVerification() {
        verificationStatus = BankAccountVerificationStatus.UNVERIFIED;
        verificationReference = null;
        verificationAmount = null;
        verificationStartedAt = null;
        verificationExpiresAt = null;
        verifiedAt = null;
        verificationAttempts = 0;
        accountHolderName = null;
        accountHolderNameVerified = false;
    }
}
