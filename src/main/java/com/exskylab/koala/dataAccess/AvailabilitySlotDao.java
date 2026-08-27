package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AvailabilitySlot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvailabilitySlotDao extends JpaRepository<AvailabilitySlot, UUID> {

    List<AvailabilitySlot> findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    Optional<AvailabilitySlot> findWithUserById(UUID id);

    void deleteAllByUserId(UUID userId);
}
