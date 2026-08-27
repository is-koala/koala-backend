package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewDao extends JpaRepository<Review, UUID> {

    boolean existsByJobAssignmentIdAndAuthorId(UUID jobAssignmentId, UUID authorId);

    @EntityGraph(attributePaths = {"author"})
    Page<Review> findAllBySubjectIdOrderByCreatedAtDesc(UUID subjectId, Pageable pageable);
}
