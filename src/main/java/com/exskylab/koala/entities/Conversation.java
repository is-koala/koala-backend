package com.exskylab.koala.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SQLDelete(sql = "UPDATE conversations SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "conversations")
public class Conversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false)
    private User employer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private User worker;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_body", length = 4000)
    private String lastMessageBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private ConversationKind kind = ConversationKind.DIRECT;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConversationParticipant> participants = new ArrayList<>();

    public void addParticipant(User user) {
        boolean alreadyIn = participants.stream()
                .anyMatch(participant -> participant.getUser().getId().equals(user.getId()));

        if (alreadyIn) {
            return;
        }

        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(this);
        participant.setUser(user);
        participant.setJoinedAt(java.time.Instant.now());
        participants.add(participant);
    }

    public boolean removeParticipant(UUID userId) {
        return participants.removeIf(participant -> participant.getUser().getId().equals(userId));
    }

    public boolean isGroup() {
        return kind == ConversationKind.GROUP;
    }

    public List<UUID> participantIds() {
        return participants.stream().map(participant -> participant.getUser().getId()).toList();
    }

    public boolean hasParticipant(UUID userId) {
        return participantIds().contains(userId);
    }

    public User counterpartOf(UUID userId) {
        return employer != null && employer.getId().equals(userId) ? worker : employer;
    }
}
