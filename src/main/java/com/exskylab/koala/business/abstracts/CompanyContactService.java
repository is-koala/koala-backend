package com.exskylab.koala.business.abstracts;


import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.User;

import java.util.UUID;

public interface CompanyContactService {
    CompanyContact addCompanyContact(Company company, User invitedUser, CompanyContactRole role);

    boolean isUserAContactOfCompany(UUID companyId, UUID userId);

    boolean isUserAdminOfCompany(UUID companyId, UUID userId);

    java.util.List<UUID> getCompanyIdsOfContact(UUID userId);

    java.util.List<CompanyContact> getContactsOfCompany(UUID companyId);

    java.util.Optional<CompanyContact> findContact(UUID companyId, UUID userId);

    long countAdminsOfCompany(UUID companyId);

    void removeContact(CompanyContact contact);

    java.util.List<CompanyContact> getMembershipsOf(UUID userId);

    java.util.List<CompanyContact> getContactsOfCompanies(java.util.Collection<UUID> companyIds);
}
