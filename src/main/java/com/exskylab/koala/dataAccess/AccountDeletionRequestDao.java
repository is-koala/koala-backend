package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AccountDeletionRequest;
import com.exskylab.koala.entities.AccountDeletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountDeletionRequestDao extends JpaRepository<AccountDeletionRequest, UUID> {

    Optional<AccountDeletionRequest> findFirstByUserIdAndStatusOrderByRequestedAtDesc(
            UUID userId, AccountDeletionStatus status);
}
