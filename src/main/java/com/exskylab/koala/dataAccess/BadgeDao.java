package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BadgeDao extends JpaRepository<Badge, UUID> {

    Optional<Badge> findByCode(String code);

    List<Badge> findAllByOrderBySortOrderAsc();
}
