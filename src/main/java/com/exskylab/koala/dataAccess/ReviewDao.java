package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewDao extends JpaRepository<Review, UUID> {

    boolean existsByJobAssignmentIdAndAuthorId(UUID jobAssignmentId, UUID authorId);

    List<Review> findAllByJobAssignmentIdOrderByCreatedAtAsc(UUID jobAssignmentId);

    List<Review> findAllByJobAssignmentIdIn(Collection<UUID> jobAssignmentIds);

    @EntityGraph(attributePaths = {"author", "author.profilePicture"})
    Page<Review> findAllBySubjectIdOrderByCreatedAtDesc(UUID subjectId, Pageable pageable);
}
