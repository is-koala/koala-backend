package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.CommercialMessageConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommercialMessageConsentDao extends JpaRepository<CommercialMessageConsent, UUID> {

    List<CommercialMessageConsent> findAllByUserIdOrderByRecordedAtDesc(UUID userId);
}
