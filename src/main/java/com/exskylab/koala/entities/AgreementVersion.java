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
@Table(name = "agreement_versions")
public class AgreementVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 48)
    private AgreementType type;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "content_url", length = 512)
    private String contentUrl;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "retired_at")
    private Instant retiredAt;
}
