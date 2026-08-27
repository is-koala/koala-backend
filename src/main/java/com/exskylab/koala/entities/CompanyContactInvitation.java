package com.exskylab.koala.entities;

import jakarta.persistence.*;
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
@Entity
@Table(name = "company_contact_invitations")
public class CompanyContactInvitation extends BaseEntity{


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedBy;

    @ManyToOne
    @JoinColumn(name = "invited_user_id", nullable = false)
    private User invitedUser;

    @Enumerated(EnumType.STRING)
    private CompanyContactRole role;

    @Enumerated(EnumType.STRING)
    private CompanyContactInvitationStatus status;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

}
