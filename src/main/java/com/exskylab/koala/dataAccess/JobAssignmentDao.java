package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.JobAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobAssignmentDao extends JpaRepository<JobAssignment, UUID> {

    @EntityGraph(attributePaths = {
            "worker",
            "job",
            "job.creator",
            "job.company",
            "job.address",
            "job.creator.address"
    })
    Optional<JobAssignment> findWithDetailsForPaymentById(UUID id);

}
