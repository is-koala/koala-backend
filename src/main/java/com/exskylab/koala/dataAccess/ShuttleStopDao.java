package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.ShuttleStop;
import com.exskylab.koala.entities.TransportDirection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShuttleStopDao extends JpaRepository<ShuttleStop, UUID> {

    @Query("""
            select st.id from ShuttleStop st
            where st.scheduledAt > :from and st.scheduledAt <= :to
              and st.shuttle.direction = :direction
              and exists (select 1 from SeatAssignment s
                          where s.stop = st
                            and s.notBoardedNoticeSentAt is null
                            and s.jobAssignment.status in :statuses)
            order by st.scheduledAt asc
            """)
    List<UUID> findNotBoardedDueStopIds(@Param("from") Instant from, @Param("to") Instant to,
                                        @Param("direction") TransportDirection direction,
                                        @Param("statuses") Collection<AssignmentStatus> statuses,
                                        Pageable pageable);

    @EntityGraph(attributePaths = {"address", "address.city", "address.city.country", "address.district"})
    List<ShuttleStop> findAllByShuttleIdInOrderBySortOrderAsc(Collection<UUID> shuttleIds);
}
