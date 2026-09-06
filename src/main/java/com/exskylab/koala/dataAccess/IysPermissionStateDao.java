package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.IysPermissionState;
import com.exskylab.koala.entities.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

@Repository
public interface IysPermissionStateDao extends JpaRepository<IysPermissionState, UUID> {

    Optional<IysPermissionState> findByUserIdAndChannel(UUID userId, NotificationChannel channel);

    List<IysPermissionState> findByNextReconciliationAtLessThanEqualOrderByNextReconciliationAtAsc(
            Instant dueBefore, Pageable pageable);
}
