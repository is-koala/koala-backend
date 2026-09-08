package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRoleDao extends JpaRepository<StaffRole, UUID> {

    List<StaffRole> findAllByCodeIn(Collection<String> codes);

    Optional<StaffRole> findByCode(String code);

    @Query(value = "select pg_advisory_xact_lock(hashtextextended('staff-separation-of-duties', 0))",
            nativeQuery = true)
    void lockSeparationOfDutiesWrites();
}
