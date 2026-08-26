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
@Table(name = "user_communication_preferences")
public class UserCommunicationPreference extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private NotificationCategory category;

    @Column(name = "email_enabled")
    private boolean emailEnabled;

    @Column(name = "sms_enabled")
    private boolean smsEnabled;

    @Column(name = "push_enabled")
    private boolean pushEnabled;

}
