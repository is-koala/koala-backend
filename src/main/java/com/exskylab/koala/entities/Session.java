package com.exskylab.koala.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@SQLDelete(sql = "UPDATE sessions SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@NotNull
@Table(name = "sessions")
public class Session extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "refresh_token_hash", length = 512)
    private String refreshTokenHash;

    @Column(name = "refresh_expires_at")
    private Instant refreshExpiresAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "location")
    private String location;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "is_active")
    private boolean isActive = true;


}
