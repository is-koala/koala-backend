package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByWorkerIdAndPaymentStatus(UUID workerId, PaymentStatus paymentStatus);

    boolean existsByWorkerIdAndStatusIn(UUID workerId, java.util.List<AssignmentStatus> statuses);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.company", "worker"})
    Page<JobAssignment> findAllByWorkerId(UUID workerId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.company", "worker"})
    Page<JobAssignment> findAllByJobCreatorId(UUID creatorId, Pageable pageable);

    long countByWorkerIdAndStatus(UUID workerId, AssignmentStatus status);
}
