package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.producers.user.UserUpdatedEventDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileSyncService {

    void enqueue(UserUpdatedEventDto event);

    List<UUID> findDueTaskIds();

    boolean runTask(UUID intentId);

    Optional<UUID> claimForProcessing(UUID intentId);

    boolean completeProcessing(UUID intentId, UUID claimId);

    void releaseProcessingClaim(UUID intentId, UUID claimId);
}
