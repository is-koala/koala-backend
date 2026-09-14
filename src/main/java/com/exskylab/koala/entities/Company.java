package com.exskylab.koala.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@SQLDelete(sql = "UPDATE companies SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "companies")
public class Company extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_id", referencedColumnName = "id")
    private Image logo;

    @Column(name = "website", length = 512)
    private String website;

    @Column(name = "description", length = 4000)
    private String description;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "address_id", referencedColumnName = "id")
    private Address address;

    @Column(name = "tax_number")
    private String taxNumber;

    @Enumerated(EnumType.STRING)
    private CompanyType type;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CompanyContact> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CompanyContactInvitation> contactInvitations = new ArrayList<>();

    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<CompanyImage> images = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private CompanyApprovalStatus approvalStatus = CompanyApprovalStatus.PENDING;

    @Column(name = "approval_note", length = 1000)
    private String approvalNote;

    @Column(name = "approval_decided_at")
    private Instant approvalDecidedAt;

    @Column(name = "approved_by_staff_id")
    private UUID approvedByStaffId;

    @Column(name = "iyzico_submerchant_key")
    private String iyzicoSubmerchantKey;

    public void addContact(CompanyContact contact){
        this.contacts.add(contact);
        contact.setCompany(this);
    }

    public void removeContact(CompanyContact contact){
        this.contacts.remove(contact);
        contact.setCompany(null);
    }


}
