package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffNote;
import com.exskylab.koala.entities.StaffResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffNoteDao extends JpaRepository<StaffNote, UUID> {

    @EntityGraph(attributePaths = {"author"})
    Page<StaffNote> findAllByResourceTypeAndResourceId(StaffResourceType resourceType, UUID resourceId,
                                                       Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Optional<StaffNote> findWithAuthorById(UUID id);
}
