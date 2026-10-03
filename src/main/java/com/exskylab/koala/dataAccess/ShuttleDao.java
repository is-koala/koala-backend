package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Shuttle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShuttleDao extends JpaRepository<Shuttle, UUID> {

    @Query(value = "select capacity from shuttles where id = :id and is_deleted = false for update", nativeQuery = true)
    Optional<Integer> lockAndReadCapacity(@Param("id") UUID id);

    @Modifying
    @Query(value = "update shuttles set qr_secret = :secret where id = :id and is_deleted = false", nativeQuery = true)
    int rotateQrSecret(@Param("id") UUID id, @Param("secret") String secret);

    @EntityGraph(attributePaths = {"plan", "plan.job"})
    Optional<Shuttle> findWithPlanJobById(UUID id);
}
