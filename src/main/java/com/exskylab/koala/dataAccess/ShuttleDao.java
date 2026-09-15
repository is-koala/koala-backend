package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Shuttle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShuttleDao extends JpaRepository<Shuttle, UUID> {

    @Query(value = "select capacity from shuttles where id = :id and is_deleted = false for update", nativeQuery = true)
    Optional<Integer> lockAndReadCapacity(@Param("id") UUID id);
}
