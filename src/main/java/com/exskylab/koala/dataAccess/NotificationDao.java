package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationDao extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :readAt
            where n.recipient.id = :recipientId and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
