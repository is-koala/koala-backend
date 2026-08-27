package com.exskylab.koala.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
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
}
