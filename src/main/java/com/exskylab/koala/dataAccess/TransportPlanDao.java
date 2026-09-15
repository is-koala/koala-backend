package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.TransportMethod;
import com.exskylab.koala.entities.TransportPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransportPlanDao extends JpaRepository<TransportPlan, UUID> {

    @Query("""
            select p.job.id from TransportPlan p
            where p.job.id in :jobIds
              and (p.outboundMethod = :method or p.returnMethod = :method)
            """)
    List<UUID> findJobIdsWithMethod(@Param("jobIds") Collection<UUID> jobIds,
                                    @Param("method") TransportMethod method);

    @EntityGraph(attributePaths = {"shuttles"})
    Optional<TransportPlan> findByJobId(UUID jobId);

    @EntityGraph(attributePaths = {"shuttles"})
    List<TransportPlan> findAllByJobIdIn(Collection<UUID> jobIds);
}
