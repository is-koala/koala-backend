package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.UserBadge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserBadgeDao extends JpaRepository<UserBadge, UUID> {

    @EntityGraph(attributePaths = {"badge"})
    List<UserBadge> findAllByUserIdOrderByBadgeSortOrderAsc(UUID userId);

    boolean existsByUserIdAndBadgeId(UUID userId, UUID badgeId);
}
