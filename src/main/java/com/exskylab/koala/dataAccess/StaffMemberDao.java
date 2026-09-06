package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffMemberDao extends JpaRepository<StaffMember, UUID> {

    Optional<StaffMember> findByKeycloakSubjectAndActiveTrue(String keycloakSubject);
}
