package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Session;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionDao extends JpaRepository<Session, UUID> {
    @EntityGraph(attributePaths = {"device", "device.user", "device.user.authorities"})
    Optional<Session> findByIdAndIsActive(UUID id, boolean active);


    List<Session> findAllByDeviceIdAndIsActive(UUID deviceId, boolean active);

    List<Session> findAllByDevice_User_IdAndIsActive(UUID userId, boolean active);

    long countByDevice_User_IdAndIsActive(UUID userId, boolean active);

    @EntityGraph(attributePaths = {"device"})
    Optional<Session> findFirstByDevice_User_IdOrderByCreatedAtDesc(UUID userId);
}
