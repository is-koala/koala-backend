package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AvailabilityException;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvailabilityExceptionDao extends JpaRepository<AvailabilityException, UUID> {

    List<AvailabilityException> findAllByUserIdAndDateGreaterThanEqualOrderByDateAsc(UUID userId, java.time.LocalDate from);

    Optional<AvailabilityException> findByUserIdAndDate(UUID userId, java.time.LocalDate date);

    @EntityGraph(attributePaths = {"user"})
    Optional<AvailabilityException> findWithUserById(UUID id);
}
