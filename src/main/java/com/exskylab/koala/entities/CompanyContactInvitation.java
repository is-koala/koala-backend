package com.exskylab.koala.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
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
@SQLDelete(sql = "UPDATE company_contact_invitations SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
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

    public boolean wasSentTo(User user) {
        return invitedUser.getId().equals(user.getId());
    }

    public boolean isPartyTo(User user) {
        return wasSentTo(user) || invitedBy.getId().equals(user.getId());
    }

    public UUID companyId() {
        return company.getId();
    }

}
