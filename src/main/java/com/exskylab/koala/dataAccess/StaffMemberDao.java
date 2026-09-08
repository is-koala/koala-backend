package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffMemberDao extends JpaRepository<StaffMember, UUID> {

    Optional<StaffMember> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select m from StaffMember m where cast(:query as String) is null "
            + "or lower(m.email) like lower(concat('%', cast(:query as String), '%')) "
            + "or lower(m.displayName) like lower(concat('%', cast(:query as String), '%'))")
    Page<StaffMember> search(@Param("query") String query, Pageable pageable);
}
