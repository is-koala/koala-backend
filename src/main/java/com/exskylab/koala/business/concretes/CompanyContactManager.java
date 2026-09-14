package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.core.events.CompanyContactAddedEvent;
import com.exskylab.koala.core.events.CompanyContactRemovedEvent;
import com.exskylab.koala.dataAccess.CompanyContactDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CompanyContactManager implements CompanyContactService {

    private final CompanyContactDao companyContactDao;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyContactManager(CompanyContactDao companyContactDao, ApplicationEventPublisher eventPublisher) {
        this.companyContactDao = companyContactDao;
        this.eventPublisher = eventPublisher;
    }


    @Override
    @Transactional
    public CompanyContact addCompanyContact(Company company, User invitedUser, CompanyContactRole role) {
        log.info("Adding company contact for company with id: {}, invited user with id: {}, role: {}", company.getId(), invitedUser.getId(), role);

        CompanyContact companyContact = new CompanyContact();
        companyContact.setCompany(company);
        companyContact.setUser(invitedUser);
        companyContact.setRole(role);
        CompanyContact savedContact = companyContactDao.save(companyContact);

        log.info("Saved company contact with id: {}", savedContact.getId());
        eventPublisher.publishEvent(new CompanyContactAddedEvent(company.getId(), invitedUser.getId()));
        return savedContact;
    }

    @Override
    public boolean isUserAContactOfCompany(UUID companyId, UUID userId) {
        log.debug("Checking if user {} is a contact of company {}", userId, companyId);
        boolean isContact = companyContactDao.existsByCompanyIdAndUserId(companyId, userId);
        log.info("Is user with id: {} a contact of company with id: {}: {}", userId, companyId, isContact);
        return isContact;
    }

    @Override
    public boolean isUserAdminOfCompany(UUID companyId, UUID userId) {
        log.debug("Checking if user {} is an admin of company {}", userId, companyId);
        boolean isAdmin = companyContactDao.existsByCompanyIdAndUserIdAndRole(companyId, userId, CompanyContactRole.ADMIN);
        log.info("Is user with id: {} an admin of company with id: {}: {}", userId, companyId, isAdmin);
        return isAdmin;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UUID> getCompanyIdsOfContact(UUID userId) {
        java.util.List<UUID> companyIds = companyContactDao.findCompanyIdsByUserId(userId);
        log.debug("User {} is a contact of {} companies", userId, companyIds.size());
        return companyIds;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<CompanyContact> getContactsOfCompany(UUID companyId) {
        return companyContactDao.findAllByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<CompanyContact> findContact(UUID companyId, UUID userId) {
        return companyContactDao.findByCompanyIdAndUserId(companyId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAdminsOfCompany(UUID companyId) {
        return companyContactDao.countByCompanyIdAndRole(companyId, CompanyContactRole.ADMIN);
    }

    @Override
    @Transactional
    public void removeContact(CompanyContact contact) {
        companyContactDao.delete(contact);
        eventPublisher.publishEvent(new CompanyContactRemovedEvent(
                contact.getCompany().getId(), contact.getUser().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyContact> getMembershipsOf(UUID userId) {
        return companyContactDao.findAllByUserId(userId);
    }
}
