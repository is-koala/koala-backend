package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContactInvitation;
import com.exskylab.koala.entities.CompanyContactInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyContactInvitationDao extends JpaRepository<CompanyContactInvitation, UUID> {
    List<CompanyContactInvitation> findByCompany(Company company);

    List<CompanyContactInvitation> findByCompanyId(UUID companyId);

    boolean existsByCompanyIdAndInvitedUserIdAndStatus(UUID companyId, UUID invitedUserId,
                                                      CompanyContactInvitationStatus status);

}
