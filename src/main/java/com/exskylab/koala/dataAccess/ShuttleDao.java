package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Shuttle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShuttleDao extends JpaRepository<Shuttle, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shuttle s where s.id = :id")
    Optional<Shuttle> findByIdForUpdate(@Param("id") UUID id);
}
