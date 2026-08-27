package com.exskylab.koala.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@SQLDelete(sql = "UPDATE user_verifications SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "verification_type", discriminatorType = DiscriminatorType.STRING)
@Table(name = "user_verifications")
public abstract class UserVerification extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name ="id")
    private UUID id;

    @Column(name = "is_verified")
    private boolean verified;

    @ManyToOne
    @JoinColumn(name= "verified_by_id")
    private User verifiedBy;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @OneToMany(mappedBy = "userVerification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VerificationDocument> verificationDocuments;

}
