package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.CommercialConsentSource;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Repository
public interface CommercialMessageConsentDao extends JpaRepository<CommercialMessageConsent, UUID> {

    List<CommercialMessageConsent> findAllByUserIdOrderByRecordedAtDesc(UUID userId);

    boolean existsByUserIdAndCategoryAndChannelAndGrantedAndSourceAndRecordedAt(
            UUID userId, NotificationCategory category, NotificationChannel channel, boolean granted,
            CommercialConsentSource source, Instant recordedAt);
}
