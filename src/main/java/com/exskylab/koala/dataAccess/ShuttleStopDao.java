package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.ShuttleStop;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShuttleStopDao extends JpaRepository<ShuttleStop, UUID> {

    @EntityGraph(attributePaths = {"address", "address.city", "address.city.country", "address.district"})
    List<ShuttleStop> findAllByShuttleIdInOrderBySortOrderAsc(Collection<UUID> shuttleIds);
}
